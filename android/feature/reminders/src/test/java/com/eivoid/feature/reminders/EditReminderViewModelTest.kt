package com.eivoid.feature.reminders

import androidx.lifecycle.SavedStateHandle
import com.eivoid.core.database.Category
import com.eivoid.core.database.ReminderEntity
import com.eivoid.core.database.RepeatRule
import com.eivoid.core.reminders.ReminderSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditReminderViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a new form is prefilled from settings`() = runTest(dispatcher.scheduler) {
        val vm = viewModel(id = 0L)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertTrue(state.isNew)
        assertEquals(4, state.draft.leadDays)
        assertEquals("INR", state.draft.currency)
    }

    @Test
    fun `blank titles are rejected`() = runTest(dispatcher.scheduler) {
        val repository = FakeReminderRepository()
        val vm = viewModel(id = 0L, repository = repository)
        advanceUntilIdle()
        vm.update { it.copy(title = "  ", merchant = "Power Co", amountText = "10") }

        vm.save()
        advanceUntilIdle()

        assertEquals("Add a name for this payment.", vm.state.value.error)
        assertTrue(repository.all.value.isEmpty())
    }

    @Test
    fun `amounts are parsed to minor units`() = runTest(dispatcher.scheduler) {
        val repository = FakeReminderRepository()
        val vm = viewModel(id = 0L, repository = repository)
        advanceUntilIdle()
        vm.update {
            it.copy(
                title = "Electricity",
                merchant = "State board",
                amountText = "12.34",
            )
        }

        vm.save()
        advanceUntilIdle()

        assertEquals(1_234L, repository.all.value.single().amountMinor)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun `saving trims text and keeps the repeat rule`() = runTest(dispatcher.scheduler) {
        val repository = FakeReminderRepository()
        val vm = viewModel(id = 0L, repository = repository)
        advanceUntilIdle()
        vm.update {
            it.copy(
                title = "  Netflix  ",
                merchant = "  Netflix India ",
                amountText = "649",
                repeatRule = RepeatRule.MONTHLY,
                category = Category.SUBSCRIPTION,
            )
        }

        vm.save()
        advanceUntilIdle()

        val saved = repository.all.value.single()
        assertEquals("Netflix", saved.title)
        assertEquals("Netflix India", saved.merchant)
        assertEquals(64_900L, saved.amountMinor)
        assertEquals(RepeatRule.MONTHLY, saved.repeatRule)
        assertEquals(Category.SUBSCRIPTION, saved.category)
    }

    @Test
    fun `an existing reminder is loaded into the draft`() = runTest(dispatcher.scheduler) {
        val repository = FakeReminderRepository()
        repository.seed(
            ReminderEntity(
                id = 9L,
                title = "Rent",
                merchant = "Landlord",
                bank = "HDFC",
                amountMinor = 2_500_000L,
                currency = "INR",
                dueAt = 1_700_000_000_000L,
                repeatRule = RepeatRule.MONTHLY,
                category = Category.RENT,
                notes = "Flat 4B",
                leadDays = 3,
                leadDaysCustomized = true,
            ),
        )
        val vm = viewModel(id = 9L, repository = repository)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isNew)
        assertEquals("Rent", state.draft.title)
        assertEquals("25000", state.draft.amountText)
        assertEquals(3, state.draft.leadDays)
        assertTrue(state.draft.leadDaysCustomized)
        assertEquals("Flat 4B", state.draft.notes)
    }

    @Test
    fun `delete removes the row and finishes`() = runTest(dispatcher.scheduler) {
        val repository = FakeReminderRepository()
        repository.seed(
            ReminderEntity(
                id = 4L,
                title = "Gym",
                merchant = "Gym Co",
                amountMinor = 100_000L,
                currency = "INR",
                dueAt = 1_700_000_000_000L,
            ),
        )
        val vm = viewModel(id = 4L, repository = repository)
        advanceUntilIdle()

        vm.delete()
        advanceUntilIdle()

        assertTrue(repository.all.value.isEmpty())
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun `changing lead days marks the row as customised`() = runTest(dispatcher.scheduler) {
        val vm = viewModel(id = 0L)
        advanceUntilIdle()

        vm.setLeadDays(7)
        advanceUntilIdle()

        assertEquals(7, vm.state.value.draft.leadDays)
        assertTrue(vm.state.value.draft.leadDaysCustomized)
    }

    @Test
    fun `date and time setters keep the other component`() = runTest(dispatcher.scheduler) {
        val vm = viewModel(id = 0L)
        advanceUntilIdle()
        val before = vm.state.value.draft.dueAt

        vm.setTime(8, 15)
        advanceUntilIdle()
        val afterTime = vm.state.value.draft.dueAt
        assertEquals(
            8,
            java.time.Instant.ofEpochMilli(afterTime)
                .atZone(java.time.ZoneId.systemDefault()).hour,
        )
        assertEquals(
            15,
            java.time.Instant.ofEpochMilli(afterTime)
                .atZone(java.time.ZoneId.systemDefault()).minute,
        )
        assertEquals(
            java.time.Instant.ofEpochMilli(before)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
            java.time.Instant.ofEpochMilli(afterTime)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
        )
    }

    @Test
    fun `parse amount rejects zero and text`() = runTest(dispatcher.scheduler) {
        val vm = viewModel(id = 0L)
        advanceUntilIdle()

        assertEquals(null, vm.parseAmount("0"))
        assertEquals(null, vm.parseAmount("abc"))
        assertEquals(50L, vm.parseAmount("0.5"))
        assertNotNull(vm.parseAmount("1"))
    }

    private fun viewModel(
        id: Long,
        repository: FakeReminderRepository = FakeReminderRepository(),
    ) = EditReminderViewModel(
        repository = repository,
        settingsStore = ReminderSettingsStore(FakePreferencesDataStore()),
        savedStateHandle = SavedStateHandle(mapOf(EditReminderViewModel.ARG_ID to id)),
    )
}
