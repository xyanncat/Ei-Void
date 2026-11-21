package com.eivoid.feature.reminders

import com.eivoid.core.database.Category
import com.eivoid.core.database.ReminderEntity
import com.eivoid.core.database.RepeatRule
import com.eivoid.core.reminders.ReminderSettings
import com.eivoid.core.reminders.ReminderSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModelTest {

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
    fun `active and paid views split the list by status`() = runTest(dispatcher.scheduler) {
        val repository = FakeReminderRepository()
        repository.seed(
            reminder(id = 1, title = "Rent", dueAt = 200L),
            reminder(id = 2, title = "Netflix", dueAt = 100L, isPaid = true),
        )
        val vm = viewModel(repository)
        backgroundScope.launch { vm.state.collect() }
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf("Rent"), state.active.map { it.title })
        assertEquals(listOf("Netflix"), state.paid.map { it.title })
        assertEquals("Rent", state.next?.title)
    }

    @Test
    fun `mark paid reaches the repository`() = runTest(dispatcher.scheduler) {
        val repository = FakeReminderRepository()
        repository.seed(reminder(id = 7, title = "Wifi"))
        val vm = viewModel(repository)
        backgroundScope.launch { vm.state.collect() }
        advanceUntilIdle()

        vm.markPaid(7L)
        advanceUntilIdle()

        assertTrue(repository.paidIds.contains(7L))
        assertTrue(vm.state.value.reminders.single().isPaid)
    }

    @Test
    fun `snooze moves the due date forward by a day`() = runTest(dispatcher.scheduler) {
        val repository = FakeReminderRepository()
        repository.seed(reminder(id = 3, title = "Power", dueAt = 1_000_000L))
        val vm = viewModel(repository)
        backgroundScope.launch { vm.state.collect() }
        advanceUntilIdle()

        vm.snooze(3L)
        advanceUntilIdle()

        assertEquals(
            1_000_000L + FakeReminderRepository.DAY,
            vm.state.value.reminders.single().dueAt,
        )
    }

    @Test
    fun `delete all clears the list`() = runTest(dispatcher.scheduler) {
        val repository = FakeReminderRepository()
        repository.seed(reminder(id = 1, title = "A"), reminder(id = 2, title = "B"))
        val vm = viewModel(repository)
        backgroundScope.launch { vm.state.collect() }
        advanceUntilIdle()

        vm.deleteAll()
        advanceUntilIdle()

        assertTrue(vm.state.value.reminders.isEmpty())
        assertEquals("All reminders deleted.", vm.state.value.message)
    }

    @Test
    fun `save inserts a reminder`() = runTest(dispatcher.scheduler) {
        val repository = FakeReminderRepository()
        val vm = viewModel(repository)
        backgroundScope.launch { vm.state.collect() }
        advanceUntilIdle()

        vm.save(reminder(id = 0, title = "New bill", dueAt = 500L))
        advanceUntilIdle()

        assertEquals("New bill", vm.state.value.reminders.single().title)
        assertNotNull(vm.state.value.reminders.single().id.takeIf { it > 0 })
    }

    @Test
    fun `sync reports success`() = runTest(dispatcher.scheduler) {
        val repository = FakeReminderRepository()
        val vm = viewModel(repository)
        backgroundScope.launch { vm.state.collect() }
        advanceUntilIdle()

        vm.syncNow()
        advanceUntilIdle()

        assertEquals("Reminders synced.", vm.state.value.message)
        assertEquals(false, vm.state.value.busy)
    }

    @Test
    fun `sync reports the failure message`() = runTest(dispatcher.scheduler) {
        val repository = FakeReminderRepository()
        repository.syncResult = Result.failure(IllegalStateException("offline"))
        val vm = viewModel(repository)
        backgroundScope.launch { vm.state.collect() }
        advanceUntilIdle()

        vm.syncNow()
        advanceUntilIdle()

        assertEquals("offline", vm.state.value.message)
    }

    @Test
    fun `default lead days are persisted and applied`() = runTest(dispatcher.scheduler) {
        val repository = FakeReminderRepository()
        val store = settingsStore()
        val vm = viewModel(repository, store)
        backgroundScope.launch { vm.state.collect() }
        advanceUntilIdle()

        vm.setDefaultLeadDays(7)
        advanceUntilIdle()

        assertEquals(7, vm.state.value.settings.defaultLeadDays)
        assertEquals(7, repository.lastDefaultLeadDays)
    }

    @Test
    fun `notification time change reschedules alarms`() = runTest(dispatcher.scheduler) {
        val repository = FakeReminderRepository()
        val vm = viewModel(repository)
        backgroundScope.launch { vm.state.collect() }
        advanceUntilIdle()

        vm.setNotifyTime(8, 30)
        advanceUntilIdle()

        assertEquals(8, vm.state.value.settings.notifyHour)
        assertEquals(30, vm.state.value.settings.notifyMinute)
        assertEquals(1, repository.rescheduleCount)
    }

    @Test
    fun `refresh permissions picks up a new snapshot`() = runTest(dispatcher.scheduler) {
        val source = FakePermissionSource()
        val vm = RemindersViewModel(
            FakeReminderRepository(),
            settingsStore(),
            source,
        )
        backgroundScope.launch { vm.state.collect() }
        advanceUntilIdle()

        source.value = ReminderPermissions(
            notificationPermissionGranted = true,
            notificationsEnabled = true,
        )
        vm.refreshPermissions()
        advanceUntilIdle()

        assertTrue(vm.state.value.permissions.canPostNotifications)
    }

    @Test
    fun `dismiss message clears it`() = runTest(dispatcher.scheduler) {
        val repository = FakeReminderRepository()
        repository.syncResult = Result.failure(IllegalStateException("nope"))
        val vm = viewModel(repository)
        backgroundScope.launch { vm.state.collect() }
        advanceUntilIdle()

        vm.syncNow()
        advanceUntilIdle()
        vm.dismissMessage()
        advanceUntilIdle()

        assertNull(vm.state.value.message)
    }

    private fun viewModel(
        repository: FakeReminderRepository,
        store: ReminderSettingsStore = settingsStore(),
        permissions: ReminderPermissionSource = FakePermissionSource(),
    ) = RemindersViewModel(repository, store, permissions)

    private fun settingsStore() = ReminderSettingsStore(FakePreferencesDataStore())

    private fun reminder(
        id: Long,
        title: String,
        dueAt: Long = System.currentTimeMillis(),
        isPaid: Boolean = false,
    ) = ReminderEntity(
        id = id,
        title = title,
        merchant = "Merchant",
        amountMinor = 1_000,
        currency = "INR",
        dueAt = dueAt,
        repeatRule = RepeatRule.NONE,
        category = Category.BILL,
        isPaid = isPaid,
        isActive = !isPaid,
    )
}
