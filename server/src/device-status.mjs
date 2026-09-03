const NETWORK_TYPES = new Set(["wifi", "cellular", "ethernet", "vpn", "other", "offline", "unknown"]);
export const ONLINE_WINDOW_MS = 11 * 60_000;

export function emptyDeviceStatus() {
  return {
    battery_level: null,
    charging: null,
    battery_temperature_c: null,
    network_type: "unknown",
    network_connected: false,
    screen_on: null,
    last_updated_at: null,
  };
}

function finiteNumber(value, minimum, maximum, field) {
  if (typeof value !== "number" || !Number.isFinite(value) || value < minimum || value > maximum) {
    throw new Error(`invalid_${field}`);
  }
  return value;
}

function boolean(value, field) {
  if (typeof value !== "boolean") throw new Error(`invalid_${field}`);
  return value;
}

export function normalizeDeviceStatus(payload, receivedAt = new Date().toISOString()) {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) throw new Error("invalid_device_status");
  const batteryLevel = finiteNumber(payload.battery_level, 0, 100, "battery_level");
  const temperature = payload.battery_temperature_c === null
    ? null
    : Math.round(finiteNumber(payload.battery_temperature_c, -50, 100, "battery_temperature_c") * 10) / 10;
  const networkType = typeof payload.network_type === "string" ? payload.network_type : "";
  if (!NETWORK_TYPES.has(networkType)) throw new Error("invalid_network_type");
  return {
    battery_level: Math.round(batteryLevel),
    charging: boolean(payload.charging, "charging"),
    battery_temperature_c: temperature,
    network_type: networkType,
    network_connected: boolean(payload.network_connected, "network_connected"),
    screen_on: boolean(payload.screen_on, "screen_on"),
    last_updated_at: receivedAt,
  };
}

export function publicDeviceStatus(status, now = Date.now()) {
  const value = { ...emptyDeviceStatus(), ...(status ?? {}) };
  const updated = Date.parse(value.last_updated_at ?? "");
  return {
    battery_level: value.battery_level,
    charging: value.charging,
    battery_temperature_c: value.battery_temperature_c,
    network_type: value.network_type,
    network_connected: Boolean(value.network_connected),
    screen_on: value.screen_on,
    online: Number.isFinite(updated) && now - updated <= ONLINE_WINDOW_MS,
    last_updated_at: value.last_updated_at,
  };
}

export class DeviceStatusService {
  constructor(store) {
    this.store = store;
  }

  async status() {
    return this.store.withLock(() => this.store.readDeviceStatus(emptyDeviceStatus()));
  }

  async report(payload) {
    return this.store.withLock(async () => {
      const receivedAt = new Date().toISOString();
      let status;
      try {
        status = normalizeDeviceStatus(payload, receivedAt);
      } catch (error) {
        return { ok: false, error: error.message };
      }
      await this.store.writeDeviceStatus(status);
      return { ok: true, status };
    });
  }
}
