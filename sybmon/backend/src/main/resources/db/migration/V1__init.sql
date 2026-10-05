create table sybase_server (
  id           bigint auto_increment primary key,
  name         varchar(100) not null unique,
  host         varchar(255) not null,
  port         int not null default 5000,
  username     varchar(100) not null,
  password_enc varchar(512) not null,
  enabled      boolean not null default true,
  created_at   timestamp not null default current_timestamp
);

create table metric_sample (
  id           bigint auto_increment primary key,
  server_id    bigint not null,
  name         varchar(64) not null,
  label        varchar(128) not null default '',
  metric_value double not null,
  ts           timestamp(3) not null,
  constraint fk_metric_server foreign key (server_id) references sybase_server(id) on delete cascade,
  index ix_metric_lookup (server_id, name, label, ts),
  index ix_metric_ts (ts)
);

create table alert_rule (
  id          bigint auto_increment primary key,
  metric_name varchar(64) not null,
  comparator  varchar(2) not null,
  threshold   double not null,
  severity    varchar(16) not null,
  enabled     boolean not null default true
);

create table alert (
  id           bigint auto_increment primary key,
  server_id    bigint not null,
  rule_id      bigint not null,
  label        varchar(128) not null default '',
  status       varchar(12) not null,
  severity     varchar(16) not null,
  message      varchar(500) not null,
  metric_value double not null,
  opened_at    timestamp(3) not null,
  resolved_at  timestamp(3) null,
  constraint fk_alert_server foreign key (server_id) references sybase_server(id) on delete cascade,
  constraint fk_alert_rule foreign key (rule_id) references alert_rule(id) on delete cascade,
  index ix_alert_status (status, opened_at),
  index ix_alert_dedupe (server_id, rule_id, label, status)
);

insert into alert_rule (metric_name, comparator, threshold, severity) values
  ('availability',        '<', 1,  'CRITICAL'),
  ('connections.blocked', '>', 5,  'WARNING'),
  ('tx.long_running',     '>', 0,  'WARNING'),
  ('db.log_used_pct',     '>', 80, 'WARNING'),
  ('db.log_used_pct',     '>', 90, 'CRITICAL'),
  ('cache.hit_ratio',     '<', 90, 'WARNING');
