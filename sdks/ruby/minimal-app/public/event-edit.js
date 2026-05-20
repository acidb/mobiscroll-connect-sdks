/* Event Edit page — vanilla JS */

// Calendar data injected by the server as a global variable (window.CAL_DATA)
document.addEventListener('DOMContentLoaded', function () {
  const providerSel  = document.getElementById('provider');
  const calendarSel  = document.getElementById('calendarId');
  const eventIdInput = document.getElementById('eventId');
  const deleteBtn    = document.getElementById('deleteBtn');
  const submitBtn    = document.getElementById('submitBtn');
  const form         = document.getElementById('event-form');
  const recurrenceCb = document.getElementById('recurrenceEnabled');
  const recurrenceEl = document.getElementById('recurrenceContent');

  // ── Provider → filter calendar dropdown ──────────────────────────────────
  providerSel.addEventListener('change', function () {
    const prov = this.value;
    calendarSel.innerHTML = '<option value="">Select a calendar…</option>';
    (window.CAL_DATA || [])
      .filter(function (c) { return !prov || c.provider === prov; })
      .forEach(function (c) {
        const o = document.createElement('option');
        o.value = c.id;
        o.textContent = c.title + ' (' + c.provider + ')';
        calendarSel.appendChild(o);
      });
  });

  // ── Toggle delete button based on eventId ─────────────────────────────────
  function syncDeleteBtn() {
    deleteBtn.disabled = !eventIdInput.value.trim();
  }
  eventIdInput.addEventListener('input', syncDeleteBtn);
  syncDeleteBtn();

  // ── Recurrence toggle ─────────────────────────────────────────────────────
  window.toggleRecurrence = function () {
    recurrenceCb.checked = !recurrenceCb.checked;
    recurrenceEl.classList.toggle('show', recurrenceCb.checked);
  };
  recurrenceCb.addEventListener('click', function (e) {
    e.stopPropagation();
    recurrenceEl.classList.toggle('show', this.checked);
  });

  // ── Result display ────────────────────────────────────────────────────────
  function showResult(type, msg) {
    const el = document.getElementById('result-box');
    el.className = 'alert alert-' + type;
    el.textContent = msg;
    el.style.display = '';
    el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  // ── Build event payload from form ─────────────────────────────────────────
  function buildPayload(isUpdate) {
    const eventId     = eventIdInput.value.trim();
    const recurringId = document.getElementById('recurringEventId').value.trim();
    const mode        = document.getElementById('mode').value;

    const payload = {
      provider:    providerSel.value,
      calendarId:  calendarSel.value,
      title:       document.getElementById('title').value,
      description: document.getElementById('description').value,
      location:    document.getElementById('location').value,
      start:       document.getElementById('startDate').value + ':00Z',
      end:         document.getElementById('endDate').value   + ':00Z',
      allDay:      document.getElementById('allDay').checked,
    };

    if (isUpdate) {
      payload.eventId = eventId;
      if (recurringId) payload.recurringEventId = recurringId;
      payload.updateMode = mode;
    }

    const attendeesRaw = document.getElementById('attendees').value.trim();
    if (attendeesRaw) {
      payload.attendees = attendeesRaw.split('\n').map(function (s) { return s.trim(); }).filter(Boolean);
    }

    if (recurrenceCb.checked) {
      payload.recurrence = {
        frequency: document.getElementById('frequency').value,
        interval:  parseInt(document.getElementById('interval').value, 10),
        count:     parseInt(document.getElementById('count').value, 10),
      };
      const byDay = document.getElementById('byDay').value.trim();
      if (byDay) {
        payload.recurrence.byDay = byDay.split(',').map(function (d) { return d.trim(); });
      }
    }

    return payload;
  }

  // ── Create / Update ───────────────────────────────────────────────────────
  form.addEventListener('submit', async function (e) {
    e.preventDefault();
    const isUpdate = !!eventIdInput.value.trim();

    submitBtn.disabled = true;
    submitBtn.textContent = 'Processing…';

    try {
      const res = await fetch('/api/events', {
        method: isUpdate ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(buildPayload(isUpdate)),
      });
      const data = await res.json();
      if (res.ok) {
        showResult('success', isUpdate
          ? 'Event updated successfully!'
          : 'Event created! ID: ' + (data.id || ''));
      } else {
        showResult('error', data.error || 'Operation failed');
      }
    } catch (err) {
      showResult('error', err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = 'Create / Update Event';
    }
  });

  // ── Delete ────────────────────────────────────────────────────────────────
  window.handleDelete = async function () {
    const eventId = eventIdInput.value.trim();
    if (!eventId || !confirm('Delete this event?')) return;

    deleteBtn.disabled = true;

    const recurringId = document.getElementById('recurringEventId').value.trim();
    const mode        = document.getElementById('mode').value;

    const payload = {
      provider:   providerSel.value,
      calendarId: calendarSel.value,
      eventId:    eventId,
      deleteMode: mode,
    };
    if (recurringId) payload.recurringEventId = recurringId;

    try {
      const res = await fetch('/api/events', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (res.ok) {
        showResult('success', 'Event deleted successfully!');
        eventIdInput.value = '';
        syncDeleteBtn();
      } else {
        const data = await res.json().catch(function () { return {}; });
        showResult('error', data.error || 'Delete failed');
        deleteBtn.disabled = false;
      }
    } catch (err) {
      showResult('error', err.message);
      deleteBtn.disabled = false;
    }
  };
});
