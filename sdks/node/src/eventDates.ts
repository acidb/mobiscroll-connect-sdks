/**
 * Returns a copy of a wire event with its ISO 8601 `start` and `end` strings converted to `Date`,
 * so responses match the declared `Date` types. Non-object input and non-string values pass through.
 *
 * @internal
 */
export function reviveEventDates<T>(event: T): T {
  if (typeof event !== 'object' || event === null) {
    return event;
  }
  const revived: Record<string, unknown> = { ...(event as Record<string, unknown>) };
  for (const key of ['start', 'end']) {
    if (typeof revived[key] === 'string') {
      revived[key] = new Date(revived[key] as string);
    }
  }
  return revived as T;
}
