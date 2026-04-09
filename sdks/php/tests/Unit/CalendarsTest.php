<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Tests\Unit;

use Mobiscroll\Connect\{Calendar};

class CalendarsTest extends BaseTestCase
{

    public function testCalendarFromArray(): void
    {
        $data = [
            'provider' => 'google',
            'id' => 'cal-123',
            'title' => 'My Calendar',
            'timeZone' => 'America/New_York',
            'color' => '#FF5733',
            'description' => 'Test calendar',
            'original' => [],
        ];

        $calendar = Calendar::fromArray($data);

        $this->assertEquals('google', $calendar->provider);
        $this->assertEquals('cal-123', $calendar->id);
        $this->assertEquals('My Calendar', $calendar->title);
        $this->assertEquals('America/New_York', $calendar->timeZone);
        $this->assertEquals('#FF5733', $calendar->color);
    }

    public function testCalendarMissingRequiredFields(): void
    {
        $this->expectException(\InvalidArgumentException::class);

        Calendar::fromArray([
            'title' => 'My Calendar',
        ]);
    }
}
