--
-- PostgreSQL database dump
--

\restrict 7eIeNodP6ka6Q0JyWBu6Imxn2KieMzi4I3xyAj40sAepvFRM5Tm9xe54ThgVKr3

-- Dumped from database version 18.6
-- Dumped by pg_dump version 18.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: branches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.branches (
    id bigint NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    address text,
    phone text,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: branches_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.branches_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: branches_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.branches_id_seq OWNED BY public.branches.id;


--
-- Name: categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categories (
    id bigint NOT NULL,
    name text NOT NULL,
    description text,
    active boolean DEFAULT true NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_by bigint
);


--
-- Name: categories_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: categories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.categories_id_seq OWNED BY public.categories.id;


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permissions (
    id bigint NOT NULL,
    name text NOT NULL,
    module text NOT NULL,
    description text NOT NULL
);


--
-- Name: permissions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.permissions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: permissions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.permissions_id_seq OWNED BY public.permissions.id;


--
-- Name: persistent_logins; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.persistent_logins (
    username character varying(64) NOT NULL,
    series character varying(64) NOT NULL,
    token character varying(64) NOT NULL,
    last_used timestamp without time zone NOT NULL
);


--
-- Name: product_images; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.product_images (
    product_id bigint NOT NULL,
    content bytea NOT NULL,
    content_type text NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.products (
    id bigint NOT NULL,
    name text NOT NULL,
    category_id bigint NOT NULL,
    price numeric(14,2) CONSTRAINT products_retail_price_not_null NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_by bigint,
    initial_stock integer DEFAULT 0 NOT NULL,
    sold_quantity integer DEFAULT 0 NOT NULL,
    image_updated_at timestamp with time zone,
    CONSTRAINT ck_products_not_oversold CHECK ((sold_quantity <= initial_stock)),
    CONSTRAINT products_initial_stock_check CHECK ((initial_stock >= 0)),
    CONSTRAINT products_retail_price_check CHECK ((price >= (0)::numeric)),
    CONSTRAINT products_sold_quantity_check CHECK ((sold_quantity >= 0))
);


--
-- Name: products_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: products_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.products_id_seq OWNED BY public.products.id;


--
-- Name: receipt_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.receipt_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permissions (
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    id bigint NOT NULL,
    name text NOT NULL,
    description text,
    system_role boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: roles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.roles_id_seq OWNED BY public.roles.id;


--
-- Name: sales; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales (
    id bigint NOT NULL,
    product_id bigint NOT NULL,
    product_name text NOT NULL,
    quantity integer NOT NULL,
    unit_price numeric(14,2) NOT NULL,
    total numeric(14,2) NOT NULL,
    sold_at timestamp with time zone DEFAULT now() NOT NULL,
    sold_by bigint,
    paid boolean DEFAULT true NOT NULL,
    paid_at timestamp with time zone,
    customer_name text,
    receipt_no bigint NOT NULL,
    CONSTRAINT sales_quantity_check CHECK ((quantity > 0)),
    CONSTRAINT sales_total_check CHECK ((total >= (0)::numeric)),
    CONSTRAINT sales_unit_price_check CHECK ((unit_price >= (0)::numeric))
);


--
-- Name: sales_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sales_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sales_id_seq OWNED BY public.sales.id;


--
-- Name: settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.settings (
    key text NOT NULL,
    value text NOT NULL,
    value_type text DEFAULT 'STRING'::text NOT NULL,
    category text NOT NULL,
    description text NOT NULL,
    critical boolean DEFAULT false NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    CONSTRAINT settings_value_type_check CHECK ((value_type = ANY (ARRAY['STRING'::text, 'INTEGER'::text, 'DECIMAL'::text, 'BOOLEAN'::text, 'JSON'::text])))
);


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_roles (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    username text NOT NULL,
    email text,
    full_name text NOT NULL,
    phone text,
    password_hash text NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    locked boolean DEFAULT false NOT NULL,
    locked_until timestamp with time zone,
    failed_login_attempts integer DEFAULT 0 NOT NULL,
    must_change_password boolean DEFAULT false NOT NULL,
    password_changed_at timestamp with time zone,
    last_login_at timestamp with time zone,
    preferred_language text DEFAULT 'en'::text NOT NULL,
    default_branch_id bigint,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_by bigint,
    CONSTRAINT users_failed_login_attempts_check CHECK ((failed_login_attempts >= 0)),
    CONSTRAINT users_preferred_language_check CHECK ((preferred_language = ANY (ARRAY['en'::text, 'rw'::text])))
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: branches id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branches ALTER COLUMN id SET DEFAULT nextval('public.branches_id_seq'::regclass);


--
-- Name: categories id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories ALTER COLUMN id SET DEFAULT nextval('public.categories_id_seq'::regclass);


--
-- Name: permissions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions ALTER COLUMN id SET DEFAULT nextval('public.permissions_id_seq'::regclass);


--
-- Name: products id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products ALTER COLUMN id SET DEFAULT nextval('public.products_id_seq'::regclass);


--
-- Name: roles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles ALTER COLUMN id SET DEFAULT nextval('public.roles_id_seq'::regclass);


--
-- Name: sales id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales ALTER COLUMN id SET DEFAULT nextval('public.sales_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Name: branches branches_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branches
    ADD CONSTRAINT branches_pkey PRIMARY KEY (id);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);


--
-- Name: persistent_logins persistent_logins_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.persistent_logins
    ADD CONSTRAINT persistent_logins_pkey PRIMARY KEY (series);


--
-- Name: product_images product_images_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_images
    ADD CONSTRAINT product_images_pkey PRIMARY KEY (product_id);


--
-- Name: products products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (id);


--
-- Name: role_permissions role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (role_id, permission_id);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: sales sales_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales
    ADD CONSTRAINT sales_pkey PRIMARY KEY (id);


--
-- Name: settings settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settings
    ADD CONSTRAINT settings_pkey PRIMARY KEY (key);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: ix_persistent_logins_username; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_persistent_logins_username ON public.persistent_logins USING btree (username);


--
-- Name: ix_products_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_products_category ON public.products USING btree (category_id);


--
-- Name: ix_sales_product; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_sales_product ON public.sales USING btree (product_id);


--
-- Name: ix_sales_receipt; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_sales_receipt ON public.sales USING btree (receipt_no);


--
-- Name: ix_sales_sold_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_sales_sold_at ON public.sales USING btree (sold_at DESC, id DESC);


--
-- Name: ix_sales_unpaid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_sales_unpaid ON public.sales USING btree (sold_at DESC) WHERE (paid = false);


--
-- Name: ix_user_roles_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_user_roles_role ON public.user_roles USING btree (role_id);


--
-- Name: ix_users_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_users_active ON public.users USING btree (enabled, deleted_at);


--
-- Name: ux_branches_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_branches_code ON public.branches USING btree (code);


--
-- Name: ux_categories_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_categories_name ON public.categories USING btree (lower(name)) WHERE (deleted_at IS NULL);


--
-- Name: ux_permissions_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_permissions_name ON public.permissions USING btree (name);


--
-- Name: ux_products_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_products_name ON public.products USING btree (lower(name)) WHERE (deleted_at IS NULL);


--
-- Name: ux_roles_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_roles_name ON public.roles USING btree (name);


--
-- Name: ux_users_email; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_users_email ON public.users USING btree (lower(email)) WHERE ((deleted_at IS NULL) AND (email IS NOT NULL));


--
-- Name: ux_users_username; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_users_username ON public.users USING btree (lower(username)) WHERE (deleted_at IS NULL);


--
-- Name: categories categories_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: categories categories_updated_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.users(id);


--
-- Name: product_images product_images_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_images
    ADD CONSTRAINT product_images_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;


--
-- Name: products products_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(id) ON DELETE RESTRICT;


--
-- Name: products products_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: products products_updated_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.users(id);


--
-- Name: role_permissions role_permissions_permission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES public.permissions(id) ON DELETE CASCADE;


--
-- Name: role_permissions role_permissions_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE CASCADE;


--
-- Name: sales sales_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales
    ADD CONSTRAINT sales_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE RESTRICT;


--
-- Name: sales sales_sold_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales
    ADD CONSTRAINT sales_sold_by_fkey FOREIGN KEY (sold_by) REFERENCES public.users(id);


--
-- Name: settings settings_updated_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settings
    ADD CONSTRAINT settings_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.users(id);


--
-- Name: user_roles user_roles_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE RESTRICT;


--
-- Name: user_roles user_roles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: users users_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: users users_default_branch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_default_branch_id_fkey FOREIGN KEY (default_branch_id) REFERENCES public.branches(id) ON DELETE RESTRICT;


--
-- Name: users users_updated_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.users(id);


--
-- PostgreSQL database dump complete
--

\unrestrict 7eIeNodP6ka6Q0JyWBu6Imxn2KieMzi4I3xyAj40sAepvFRM5Tm9xe54ThgVKr3

