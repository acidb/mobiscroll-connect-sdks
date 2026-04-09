<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Resources;

use Mobiscroll\Connect\{ApiClient, CalendarEvent};

class Events
{
    public function __construct(private ApiClient $apiClient)
    {
    }

    /**
     * Format DateTime to ISO 8601 string or return as-is if already a string
     *
     * @param \DateTime|string $date
     * @return string
     */
    private function formatDate(\DateTime|string $date): string
    {
        return $date instanceof \DateTime
            ? $date->format('Y-m-d\TH:i:s\Z')
            : (string)$date;
    }

    /**
     * Extract event from API response or throw error
     *
     * @param mixed $response
     * @param string $operation Operation name for error message
     * @return CalendarEvent
     */
    private function extractEventFromResponse(mixed $response, string $operation): CalendarEvent
    {
        if (is_array($response) && isset($response['event'])) {
            return CalendarEvent::fromArray($response['event']);
        }

        throw new \Mobiscroll\Connect\Exceptions\ServerError(
            is_array($response) && isset($response['message']) ? $response['message'] : "Failed to {$operation} event",
            400
        );
    }

    /**
     * Build query parameters for the list endpoint.
     *
     * @param array<string, mixed>|null $params
     * @return array<string, string>
     */
    private function buildListQuery(?array $params = null): array
    {
        /** @var array<string, string> $query */
        $query = [];

        if ($params === null) {
            return $query;
        }

        // Format start date as ISO 8601
        if (isset($params['start'])) {
            $query['start'] = $this->formatDate($params['start']);
        }

        // Format end date as ISO 8601
        if (isset($params['end'])) {
            $query['end'] = $this->formatDate($params['end']);
        }

        // Node SDK style: calendarIds object keyed by provider.
        if (isset($params['calendarIds'])) {
            $encoded = json_encode($params['calendarIds']);
            $query['calendarIds'] = $encoded !== false ? $encoded : '{}';
        // Backward-compatible alias used by earlier PHP minimal app code.
        } elseif (isset($params['calendars'])) {
            $encoded = json_encode((array)$params['calendars']);
            $query['calendarIds'] = $encoded !== false ? $encoded : '[]';
        }

        // Page size limit
        if (isset($params['pageSize'])) {
            $pageSize = (int)$params['pageSize'];
            $query['pageSize'] = (string)min($pageSize, 1000);
        }

        // Pagination token
        if (isset($params['nextPageToken'])) {
            $query['nextPageToken'] = (string)$params['nextPageToken'];
        }

        // Recurring event expansion (default: true)
        if (isset($params['singleEvents'])) {
            $query['singleEvents'] = $params['singleEvents'] ? 'true' : 'false';
        }

        return $query;
    }

    /**
     * List calendar events from all connected providers
     *
     * @param array<string, mixed>|null $params Query parameters:
     *   - start: \DateTime|string Event range start (ISO 8601)
     *   - end: \DateTime|string Event range end (ISO 8601)
     *   - calendars: string[] Array of calendar IDs
     *   - pageSize: int Events per page (default: 250, max: 1000)
     *   - nextPageToken: string For pagination
     *   - singleEvents: bool Expand recurring events (default: true)
     * @return array<string, mixed>
     */
    public function list(?array $params = null): array
    {
        $query = $this->buildListQuery($params);
        $response = $this->apiClient->get('/events', $query ?: null);

        /** @var array<string, mixed> $data */
        $data = is_array($response) ? $response : [];
        return $data;
    }

    /**
     * Create a calendar event
     *
     * @param array<string, mixed> $event Event data:
     *   - provider: string ('google'|'microsoft'|'apple'|'caldav') REQUIRED
     *   - calendarId: string Calendar ID REQUIRED
     *   - title: string Event title REQUIRED
     *   - start: \DateTime|string Start time REQUIRED
     *   - end: \DateTime|string End time REQUIRED
     *   - description?: string
     *   - location?: string
     *   - attendees?: array<{email: string, name?: string}>
     *   - timezone?: string
     *   - allDay?: bool
     * @return CalendarEvent
     */
    public function create(array $event): CalendarEvent
    {
        // Format DateTime fields to ISO 8601 strings
        if (isset($event['start'])) {
            $event['start'] = $this->formatDate($event['start']);
        }
        if (isset($event['end'])) {
            $event['end'] = $this->formatDate($event['end']);
        }

        $response = $this->apiClient->post('/event', $event);
        return $this->extractEventFromResponse($response, 'create');
    }

    /**
     * Update an existing calendar event
     *
     * @param array<string, mixed> $event Event data:
     *   - provider: string ('google'|'microsoft'|'apple'|'caldav') REQUIRED
     *   - calendarId: string Calendar ID REQUIRED
     *   - eventId: string Event ID REQUIRED
     *   - title?: string
     *   - start?: \DateTime|string
     *   - end?: \DateTime|string
     *   - description?: string
     *   - location?: string
     * @return CalendarEvent
     */
    public function update(array $event): CalendarEvent
    {
        // Format DateTime fields to ISO 8601 strings
        if (isset($event['start'])) {
            $event['start'] = $this->formatDate($event['start']);
        }
        if (isset($event['end'])) {
            $event['end'] = $this->formatDate($event['end']);
        }

        $response = $this->apiClient->put('/event', $event);
        return $this->extractEventFromResponse($response, 'update');
    }

    /**
     * Delete a calendar event.
     *
     * Node-style preferred usage:
     * - delete(['provider' => 'google', 'calendarId' => '...', 'eventId' => '...'])
     *
     * Backward-compatible usage:
     * - delete('google', 'calendar-id', 'event-id')
     *
     * Optional recurring parameters (array mode):
     * - recurringEventId
     * - deleteMode
     *
     * @param array<string, mixed>|string $paramsOrProvider
     * @return array<string, mixed>
     */
    public function delete(array|string $paramsOrProvider, ?string $calendarId = null, ?string $eventId = null): array
    {
        if (is_array($paramsOrProvider)) {
            $query = [];
            foreach (['provider', 'calendarId', 'eventId', 'recurringEventId', 'deleteMode'] as $key) {
                if (isset($paramsOrProvider[$key]) && $paramsOrProvider[$key] !== '') {
                    $query[$key] = (string)$paramsOrProvider[$key];
                }
            }
        } else {
            if ($calendarId === null || $eventId === null) {
                throw new \InvalidArgumentException('calendarId and eventId are required when using legacy delete signature.');
            }

            $query = [
                'provider' => $paramsOrProvider,
                'calendarId' => $calendarId,
                'eventId' => $eventId,
            ];
        }

        foreach (['provider', 'calendarId', 'eventId'] as $required) {
            if (!isset($query[$required]) || $query[$required] === '') {
                throw new \InvalidArgumentException("{$required} is required for event deletion.");
            }
        }

        $response = $this->apiClient->delete('/event', null, $query);

        // Response contains { success: bool, message?: string }
        // If not successful, throw detailed error
        if (is_array($response) && isset($response['success']) && !$response['success']) {
            throw new \Mobiscroll\Connect\Exceptions\ServerError(
                $response['message'] ?? 'Failed to delete event',
                400
            );
        }

        /** @var array<string, mixed> $data */
        $data = is_array($response) ? $response : [];
        return $data;
    }
}
