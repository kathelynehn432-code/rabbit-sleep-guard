import assert from "node:assert/strict";
import test from "node:test";
import { normalizeDeviceStatus, ONLINE_WINDOW_MS, publicDeviceStatus } from "../src/device-status.mjs";

const sample = {
  battery_level: 73.4,
  charging: true,
  battery_temperature_c: 31.26,
  network_type: "wifi",
  network_connected: true,
  screen_on: false,
};

test("phone reports are normalized and online only inside the reporting window", () => {
  const reported = normalizeDeviceStatus(sample, "2026-09-03T10:00:00.000Z");
  assert.equal(reported.battery_level, 73);
  assert.equal(reported.battery_temperature_c, 31.3);
  assert.equal(publicDeviceStatus(reported, Date.parse(reported.last_updated_at) + ONLINE_WINDOW_MS).online, true);
  assert.equal(publicDeviceStatus(reported, Date.parse(reported.last_updated_at) + ONLINE_WINDOW_MS + 1).online, false);
});

test("phone reports reject malformed sensor values", () => {
  assert.throws(() => normalizeDeviceStatus({ ...sample, battery_level: 101 }), /invalid_battery_level/);
  assert.throws(() => normalizeDeviceStatus({ ...sample, network_type: "5g-ish" }), /invalid_network_type/);
  assert.throws(() => normalizeDeviceStatus({ ...sample, screen_on: "yes" }), /invalid_screen_on/);
});
