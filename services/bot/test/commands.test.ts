import { describe, expect, it } from 'vitest';
import { commandData } from '../src/commands.js';

interface CommandOption {
  name: string;
  type: number;
  required?: boolean;
  min_value?: number;
  max_value?: number;
}

function command(name: string) {
  const found = commandData.find((entry) => entry.name === name);
  expect(found, `/${name} should be registered`).toBeDefined();
  return found!;
}

function options(name: string): CommandOption[] {
  return (command(name).options ?? []) as unknown as CommandOption[];
}

describe('command data', () => {
  it('registers every unified command', () => {
    expect(commandData.map((entry) => entry.name).sort()).toEqual([
      'about',
      'link',
      'paid',
      'reminders',
      'snooze',
      'usage',
    ]);
  });

  it('defines /snooze with bounded options', () => {
    const localId = options('snooze').find((option) => option.name === 'local_id');
    const days = options('snooze').find((option) => option.name === 'days');

    expect(localId).toMatchObject({
      type: 4,
      required: true,
      min_value: 1,
      max_value: Number.MAX_SAFE_INTEGER,
    });
    expect(days).toMatchObject({ type: 4, min_value: 1, max_value: 30 });
    expect(Boolean(days?.required)).toBe(false);
  });

  it('defines /paid with a required local_id', () => {
    const localId = options('paid').find((option) => option.name === 'local_id');
    expect(localId).toMatchObject({
      type: 4,
      required: true,
      min_value: 1,
      max_value: Number.MAX_SAFE_INTEGER,
    });
    expect(options('paid')).toHaveLength(1);
  });

  it('keeps /usage, /link, /reminders and /about option-free', () => {
    for (const name of ['usage', 'link', 'reminders', 'about']) {
      expect(options(name)).toHaveLength(0);
    }
  });
});
