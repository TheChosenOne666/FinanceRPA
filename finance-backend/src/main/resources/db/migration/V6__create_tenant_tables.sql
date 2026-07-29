SET search_path = finrpa;

-- 组织表（租户主表，本身不参与租户隔离过滤）
CREATE TABLE IF NOT EXISTS enterprise_organization (
    id BIGSERIAL PRIMARY KEY,
    org_id BIGINT NOT NULL,
    org_name VARCHAR(128) NOT NULL,
    org_code VARCHAR(64) NOT NULL,
    description VARCHAR(256),
    status SMALLINT DEFAULT 1 NOT NULL,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (org_id),
    UNIQUE (org_code)
);

-- 部门表（属于某个组织，参与租户隔离过滤）
CREATE TABLE IF NOT EXISTS enterprise_department (
    id BIGSERIAL PRIMARY KEY,
    dept_id BIGINT NOT NULL,
    org_id BIGINT NOT NULL,
    dept_name VARCHAR(128) NOT NULL,
    dept_code VARCHAR(64) NOT NULL,
    parent_id BIGINT DEFAULT 0,
    sort_order INT DEFAULT 0,
    status SMALLINT DEFAULT 1 NOT NULL,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (dept_id)
);

-- 业务线表（属于某个组织，参与租户隔离过滤）
CREATE TABLE IF NOT EXISTS enterprise_business_line (
    id BIGSERIAL PRIMARY KEY,
    business_line_id BIGINT NOT NULL,
    org_id BIGINT NOT NULL,
    business_line_name VARCHAR(128) NOT NULL,
    business_line_code VARCHAR(64) NOT NULL,
    description VARCHAR(256),
    sort_order INT DEFAULT 0,
    status SMALLINT DEFAULT 1 NOT NULL,
    deleted SMALLINT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (business_line_id)
);

CREATE INDEX IF NOT EXISTS idx_enterprise_department_org ON enterprise_department(org_id);
CREATE INDEX IF NOT EXISTS idx_enterprise_business_line_org ON enterprise_business_line(org_id);
