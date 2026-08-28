import { bearer, json, readJson, sameSecret } from "./http-utils.mjs";
import { publicState } from "./guard-state.mjs";

const PROTOCOL_VERSIONS = new Set(["2025-06-18", "2025-03-26"]);
const DEFAULT_PROTOCOL_VERSION = "2025-03-26";

const tools = [
  {
    name: "activate_sleep_guard",
    title: "开启兔酱睡眠守卫",
    description: "当使用者明确说晚安、准备睡觉，或要求开始管手机时调用。应在回复最终晚安之前开启。省略 ends_at 时固定在下一个北京时间早上 06:30 自动解除；只有使用者当晚另行指定结束时间时才传 ends_at。",
    inputSchema: {
      type: "object",
      properties: {
        ends_at: { type: "string", description: "仅在使用者另行指定时填写的 ISO 8601 结束时间，必须在未来 24 小时内；默认留空以使用下一个北京时间 06:30。" },
      },
      additionalProperties: false,
    },
    annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "deactivate_sleep_guard",
    title: "解除兔酱睡眠守卫",
    description: "当使用者明确说早安、已经起床，或明确要求解除睡眠守卫时调用。不要因为普通聊天或查询而解除。",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
    annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  },
  {
    name: "get_sleep_guard_status",
    title: "查询兔酱睡眠守卫",
    description: "查询守卫是否开启、今晚拦截次数、开始时间和结束时间。",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
    annotations: { readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false },
  },
];

function result(id, value) {
  return { jsonrpc: "2.0", id: id ?? null, result: value };
}

function error(id, code, message) {
  return { jsonrpc: "2.0", id: id ?? null, error: { code, message } };
}

function textResult(text, structuredContent, isError = false) {
  return { isError, content: [{ type: "text", text }], structuredContent };
}

async function callTool(name, args, guard, source) {
  if (name === "activate_sleep_guard") {
    const transition = await guard.event({ event: "sleep_guard_started", ends_at: args?.ends_at }, source);
    if (!transition.ok) return textResult(`睡眠守卫开启失败：${transition.error}`, transition, true);
    const state = publicState(transition.state);
    return textResult(`睡眠守卫已经开启，将在 ${state.ends_at} 自动解除。`, state);
  }
  if (name === "deactivate_sleep_guard") {
    const transition = await guard.event({ event: "sleep_guard_ended" }, source);
    if (!transition.ok) return textResult(`睡眠守卫解除失败：${transition.error}`, transition, true);
    return textResult("睡眠守卫已经解除。", publicState(transition.state));
  }
  if (name === "get_sleep_guard_status") {
    const state = publicState(await guard.status());
    return textResult(
      state.active ? `睡眠守卫正在运行，已拦截 ${state.attempts} 次。` : `睡眠守卫当前未开启，最近记录 ${state.attempts} 次。`,
      state,
    );
  }
  return textResult("未知工具。", { error: "unknown_tool" }, true);
}

async function handleMessage(message, guard, source) {
  if (!message || typeof message !== "object") return error(null, -32600, "Invalid Request");
  const id = message.id;
  if (id === undefined) return null;
  if (message.method === "initialize") {
    const requested = message.params?.protocolVersion;
    return result(id, {
      protocolVersion: PROTOCOL_VERSIONS.has(requested) ? requested : DEFAULT_PROTOCOL_VERSION,
      capabilities: { tools: { listChanged: false } },
      serverInfo: { name: "rabbit-sleep-guard", version: "0.1.0" },
      instructions: "使用者明确说晚安或准备睡觉时，先调用 activate_sleep_guard，默认不传 ends_at，由服务器锁到下一个北京时间早上 06:30；只有使用者当晚另行指定时间时才传 ends_at。明确说早安、已起床或要求解除时调用 deactivate_sleep_guard；询问守卫或次数时调用 get_sleep_guard_status。不要自行解除。",
    });
  }
  if (message.method === "ping") return result(id, {});
  if (message.method === "tools/list") return result(id, { tools });
  if (message.method === "tools/call") {
    return result(id, await callTool(message.params?.name, message.params?.arguments ?? {}, guard, source));
  }
  return error(id, -32601, "Method not found");
}

export async function handleMcp(request, response, guard, oauth, config) {
  const supplied = bearer(request);
  const serverCodex = supplied && sameSecret(supplied, config.codexControlToken);
  const oauthAccess = serverCodex ? false : await oauth.hasOAuthAccess(request);
  if (!serverCodex && !oauthAccess) {
    return json(response, 401, { error: "unauthorized" }, {
      "www-authenticate": `Bearer resource_metadata="${config.publicBaseUrl}/.well-known/oauth-protected-resource/mcp", scope="sleep_guard:write"`,
    });
  }
  if (request.method !== "POST") return json(response, 405, { error: "method_not_allowed" }, { allow: "POST" });
  let input;
  try {
    input = await readJson(request);
  } catch {
    return json(response, 400, error(null, -32700, "Parse error"));
  }
  const source = serverCodex ? "server_codex_mcp" : "chatgpt_oauth_mcp";
  if (Array.isArray(input)) {
    const output = (await Promise.all(input.map((item) => handleMessage(item, guard, source)))).filter(Boolean);
    return json(response, output.length ? 200 : 202, output);
  }
  const output = await handleMessage(input, guard, source);
  if (!output) {
    response.writeHead(202, { "cache-control": "no-store" });
    return response.end();
  }
  return json(response, 200, output);
}

export { tools as mcpTools };
