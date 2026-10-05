import { useCallback, useEffect, useState } from "react";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api } from "./api";

/** Polls an async fn on an interval; keeps the last good data on transient errors. */
function usePoll(fn, deps, ms = 15000) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  useEffect(() => {
    let live = true;
    const run = () =>
      fn()
        .then((d) => live && (setData(d), setError(null)))
        .catch((e) => live && setError(e.message));
    setData(null);
    run();
    const t = setInterval(run, ms);
    return () => { live = false; clearInterval(t); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return { data, error };
}

const fmt = (n, d = 1) => (n == null ? "–" : Number(n).toFixed(d));

function Card({ title, value, tone }) {
  return (
    <div className={`card ${tone ?? ""}`}>
      <div className="card-title">{title}</div>
      <div className="card-value">{value}</div>
    </div>
  );
}

function Overview({ id }) {
  const { data, error } = usePoll(() => api.get(`/servers/${id}/summary`), [id]);
  if (error) return <p className="err">{error}</p>;
  if (!data) return <p>Loading…</p>;
  if (data.length === 0) return <p>No data yet — the first poll runs within 30 seconds of adding a server.</p>;

  const g = (n) => data.find((m) => m.name === n && m.label === "")?.value;
  const up = g("availability") === 1;
  const dbs = {};
  data.filter((m) => m.name.startsWith("db.")).forEach((m) => ((dbs[m.label] ??= {})[m.name] = m.value));
  const caches = data.filter((m) => m.name === "cache.hit_ratio");

  return (
    <>
      <div className="cards">
        <Card title="Status" value={up ? "UP" : "DOWN"} tone={up ? "ok" : "bad"} />
        <Card title="Connections" value={fmt(g("connections.total"), 0)} />
        <Card title="Blocked" value={fmt(g("connections.blocked"), 0)} tone={g("connections.blocked") > 5 ? "warn" : ""} />
        <Card title="Long tx (>5m)" value={fmt(g("tx.long_running"), 0)} tone={g("tx.long_running") > 0 ? "warn" : ""} />
      </div>

      <h3>Databases</h3>
      <table>
        <thead><tr><th>Database</th><th>Size (MB)</th><th>Log used</th></tr></thead>
        <tbody>
          {Object.entries(dbs).sort().map(([name, m]) => (
            <tr key={name}>
              <td>{name}</td>
              <td>{fmt(m["db.size_mb"], 0)}</td>
              <td className={m["db.log_used_pct"] > 90 ? "bad" : m["db.log_used_pct"] > 80 ? "warn" : ""}>
                {m["db.log_used_pct"] == null ? "–" : `${fmt(m["db.log_used_pct"])}%`}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {caches.length > 0 && (
        <>
          <h3>Data caches (hit ratio since start)</h3>
          <table>
            <tbody>{caches.map((c) => <tr key={c.label}><td>{c.label}</td><td>{fmt(c.value)}%</td></tr>)}</tbody>
          </table>
        </>
      )}
    </>
  );
}

function Metrics({ id }) {
  const { data: sum } = usePoll(() => api.get(`/servers/${id}/summary`), [id], 60000);
  const [key, setKey] = useState("");
  const [minutes, setMinutes] = useState(60);
  const options = (sum ?? []).map((m) => `${m.name}|${m.label}`);
  const cur = key || options[0];
  const [name, label = ""] = (cur ?? "").split("|");

  const { data, error } = usePoll(
    () => (cur ? api.get(`/servers/${id}/metrics?name=${encodeURIComponent(name)}&label=${encodeURIComponent(label)}&minutes=${minutes}`) : Promise.resolve([])),
    [id, cur, minutes],
    30000
  );
  const points = (data ?? []).map((p) => ({ t: new Date(p.ts).toLocaleTimeString(), v: p.value }));

  return (
    <>
      <div className="row">
        <select value={cur ?? ""} onChange={(e) => setKey(e.target.value)}>
          {options.map((o) => <option key={o} value={o}>{o.replace("|", " · ")}</option>)}
        </select>
        <select value={minutes} onChange={(e) => setMinutes(+e.target.value)}>
          <option value={60}>1 hour</option><option value={360}>6 hours</option>
          <option value={1440}>24 hours</option><option value={10080}>7 days</option>
        </select>
      </div>
      {error && <p className="err">{error}</p>}
      <div style={{ height: 320 }}>
        <ResponsiveContainer>
          <LineChart data={points}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="t" minTickGap={40} />
            <YAxis />
            <Tooltip />
            <Line type="monotone" dataKey="v" dot={false} isAnimationActive={false} stroke="#2563eb" />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </>
  );
}

function Live({ id }) {
  const [mode, setMode] = useState("processes");
  const { data, error } = usePoll(() => api.get(`/servers/${id}/live/${mode}`), [id, mode], 10000);
  const cols = data?.[0] ? Object.keys(data[0]) : [];
  return (
    <>
      <div className="row">
        <button className={mode === "processes" ? "on" : ""} onClick={() => setMode("processes")}>Top processes</button>
        <button className={mode === "blocking" ? "on" : ""} onClick={() => setMode("blocking")}>Blocking chains</button>
      </div>
      {error && <p className="err">{error}</p>}
      {data && data.length === 0 && <p>Nothing to show.</p>}
      {cols.length > 0 && (
        <div className="scroll">
          <table>
            <thead><tr>{cols.map((c) => <th key={c}>{c}</th>)}</tr></thead>
            <tbody>{data.map((r, i) => <tr key={i}>{cols.map((c) => <td key={c}>{String(r[c] ?? "")}</td>)}</tr>)}</tbody>
          </table>
        </div>
      )}
    </>
  );
}

function Alerts({ servers }) {
  const [tick, setTick] = useState(0);
  const { data, error } = usePoll(() => api.get("/alerts"), [tick], 15000);
  const name = (id) => servers.find((s) => s.id === id)?.name ?? id;
  const ack = (id) => api.post(`/alerts/${id}/ack`).then(() => setTick((t) => t + 1));
  return (
    <>
      {error && <p className="err">{error}</p>}
      <table>
        <thead><tr><th>Opened</th><th>Server</th><th>Severity</th><th>Message</th><th>Status</th><th /></tr></thead>
        <tbody>
          {(data ?? []).map((a) => (
            <tr key={a.id}>
              <td>{new Date(a.openedAt).toLocaleString()}</td>
              <td>{name(a.serverId)}</td>
              <td className={a.severity === "CRITICAL" ? "bad" : "warn"}>{a.severity}</td>
              <td>{a.message}</td>
              <td>{a.status}</td>
              <td>{a.status === "OPEN" && <button onClick={() => ack(a.id)}>Ack</button>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}

function ServerForm({ onSaved, onCancel }) {
  const [f, setF] = useState({ name: "", host: "", port: 5000, username: "", password: "" });
  const [err, setErr] = useState(null);
  const set = (k) => (e) => setF({ ...f, [k]: k === "port" ? +e.target.value : e.target.value });
  const submit = () => api.post("/servers", f).then(onSaved).catch((e) => setErr(e.message));
  return (
    <div className="form">
      <input placeholder="Display name" value={f.name} onChange={set("name")} />
      <input placeholder="Host" value={f.host} onChange={set("host")} />
      <input placeholder="Port" type="number" value={f.port} onChange={set("port")} />
      <input placeholder="Monitoring login" value={f.username} onChange={set("username")} />
      <input placeholder="Password" type="password" value={f.password} onChange={set("password")} />
      {err && <p className="err">{err}</p>}
      <div className="row"><button onClick={submit}>Add</button><button onClick={onCancel}>Cancel</button></div>
    </div>
  );
}

export default function App() {
  const [servers, setServers] = useState([]);
  const [sel, setSel] = useState(null);
  const [tab, setTab] = useState("overview");
  const [adding, setAdding] = useState(false);
  const [msg, setMsg] = useState(null);

  const load = useCallback(
    () => api.get("/servers").then((s) => { setServers(s); setSel((cur) => cur ?? s[0]?.id ?? null); }),
    []
  );
  useEffect(() => { load(); }, [load]);

  const server = servers.find((s) => s.id === sel);
  const test = () => api.post(`/servers/${sel}/test`).then((r) => setMsg(`${r.ok ? "OK" : "FAILED"}: ${r.detail}`));
  const remove = () => confirm(`Remove ${server.name} and all its history?`) &&
    api.del(`/servers/${sel}`).then(() => { setSel(null); load(); });

  return (
    <div className="app">
      <aside>
        <h2>Sybase Monitor</h2>
        {servers.map((s) => (
          <div key={s.id} className={`srv ${s.id === sel ? "on" : ""}`} onClick={() => { setSel(s.id); setMsg(null); }}>
            {s.name}<small>{s.host}:{s.port}</small>
          </div>
        ))}
        {adding
          ? <ServerForm onSaved={() => { setAdding(false); load(); }} onCancel={() => setAdding(false)} />
          : <button onClick={() => setAdding(true)}>+ Add server</button>}
        <button className={tab === "alerts" ? "on" : ""} onClick={() => setTab("alerts")}>All alerts</button>
      </aside>

      <main>
        {tab === "alerts" ? (
          <Alerts servers={servers} />
        ) : !server ? (
          <p>Add a Sybase server to get started.</p>
        ) : (
          <>
            <div className="row">
              <h2>{server.name}</h2>
              <button onClick={test}>Test connection</button>
              <button onClick={remove}>Remove</button>
            </div>
            {msg && <p>{msg}</p>}
            <nav>
              {["overview", "metrics", "live"].map((t) => (
                <button key={t} className={tab === t ? "on" : ""} onClick={() => setTab(t)}>{t}</button>
              ))}
            </nav>
            {tab === "overview" && <Overview id={sel} />}
            {tab === "metrics" && <Metrics id={sel} />}
            {tab === "live" && <Live id={sel} />}
          </>
        )}
      </main>
    </div>
  );
}
