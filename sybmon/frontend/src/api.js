// Same-origin calls; the browser attaches HTTP Basic credentials after the first 401 prompt.
async function req(path, method = "GET", body) {
  const r = await fetch("/api" + path, {
    method,
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!r.ok) throw new Error((await r.text()) || r.statusText);
  return r.status === 204 ? null : r.json();
}

export const api = {
  get: (p) => req(p),
  post: (p, b) => req(p, "POST", b ?? {}),
  put: (p, b) => req(p, "PUT", b),
  del: (p) => req(p, "DELETE"),
};
