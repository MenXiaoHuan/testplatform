# Drop Legacy Repository Runtime Fields Design

**Context**

The repository model still carries legacy `packageManager` and `nodeVersion` fields even though repository execution now depends on `workingDirectory`, `installCommand`, `runCommandTemplate`, `testRoot`, and report-relative paths. These legacy fields are hidden from the UI but still exist in the database schema, JPA entity, service defaults, controller tests, and repository list rendering.

**Goal**

Remove `packageManager` and `nodeVersion` completely from the platform repository model so the UI, API, service layer, and database schema reflect the actual product model without hidden compatibility fields.

**Non-Goals**

- Do not change Playwright artifact metadata or report content that may contain unrelated Node version data.
- Do not change repository execution behavior beyond removing dependence on the deleted fields.
- Do not introduce a staged migration. This is a one-step removal.

**Design**

1. Database:
   - Add a new Flyway migration that drops `package_manager` and `node_version` from `test_repository`.
2. Backend:
   - Remove the two fields from `TestRepositoryEntity`.
   - Remove default-filling logic from `RepositoryServiceImpl`.
   - Keep create/update behavior based only on active repository fields.
3. Frontend:
   - Remove the `包管理器` column from the repository list view.
   - Keep repository form focused on active execution fields only.
4. Tests:
   - Update controller and service tests to stop sending, asserting, or defaulting the legacy fields.
   - Update frontend view tests and source-based width assertions to reflect the new repository table structure.

**Validation**

- Backend focused tests:
  - `RepositoryControllerTest`
  - `RepositoryServiceImplTest`
  - `EntitySchemaMappingTest`
- Frontend focused tests:
  - `RepositoryListView.test.ts`
  - `listColumnWidth.test.ts`
- Diagnostics:
  - `TestRepositoryEntity.java`
  - `RepositoryServiceImpl.java`
  - `RepositoryListView.vue`
