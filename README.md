Metadata Export
================

**Active development / experimental.** This module is an early proof of concept. APIs, output
format, and behaviour will change without notice, and not everything described here is fully
verified yet. Not for production use.

Description
-----------
Initializer ([openmrs-module-initializer](https://github.com/mekomsolutions/openmrs-module-initializer))
can *load* a `configuration/` content package into OpenMRS, but it cannot produce one. This module
does the reverse: it reads metadata out of a running, populated OpenMRS instance and writes it back
out in the Initializer format, so a configuration can be captured from a server and replayed
elsewhere.

It is export only. It never imports or applies metadata; loading remains Initializer's job.

Currently supported domains:

* Concepts (names, descriptions, class/datatype/version, numeric, complex, answers,
  mappings, attributes)
* Concept sources (name, description, HL7 code, unique ID)
* Encounter types (name, description, view/edit privileges)
* Privileges (name, description)
* Concept classes (name, description)
* Roles (name, description, privileges)
* Patient identifier types (name, description, required, format, format description, validator,
    location/uniqueness behavior)
* Visit types (name, description)
* Relationship types (name, description, a_is_to_b, b_is_to_a, preferred, weight)
* Attribute types (name, description, Min occurs, Max occurs, Datatype classname, Datatype config, Preferred handler classname, Handler config)
* Global properties (property, value) — written as XML, since Initializer loads this domain from
  XML rather than CSV
* Encounter Roles (name, description)
* Person Attribute Types (name, description, searchable, format, foreign uuid, edit privilege)
* Location Tags (name, description)
* Locations (name, description, parent location, tags, address fields) — parent locations and tags
  are pulled in via cross-domain closure. Tag membership is emitted inline as `Tag|<name>` columns,
  which is Initializer's own equivalent of the standalone `locationtagmaps` domain, so that data
  needs no separate file
* Drugs (name, description, strength, concept drug, concept dosage form, ingredients, mappings) —
  drug/dosage-form/ingredient concepts are pulled in via cross-domain closure
* Order types (name, description, java class name, parent, concept classes) — parent order types and
  concept classes are pulled in via cross-domain closure
* Flags (name, criteria, evaluator, message, priority, enabled, tags, description)
* Order frequencies (frequency per day, concept frequency) — the referenced concept is pulled in via
  cross domain closure
* Programs (program concept, outcomes concept) — the referenced concepts are pulled in via
  cross domain closure
* Program workflows (program, workflow concept) — the referenced program and concept are pulled in
  via cross-domain closure
* Concept reference ranges (concept numeric, absolute/critical/normal low and high, criteria) — the
  referenced concept numeric is pulled in via cross domain closure
* Concept sets (parent concept, member concept, member type, sort weight) — the referenced parent
  and member concepts are pulled in via cross domain closure
* Program workflow states (workflow, state concept, initial, terminal) — the referenced workflow and
  state concept are pulled in via cross domain closure
* Procedure types (name, description) — requires the emrapi module (3.4+)
* Metadata sets (name, description) — requires the metadatamapping module
* Metadata set members (name, description, sort weight, metadata class, metadata uuid, metadata
  set uuid) — the owning metadata set and the referenced metadata item are pulled in via
  cross-domain closure; requires the metadatamapping module
* Metadata term mappings (mapping code, mapping source, metadata class name, metadata uuid) — the
  referenced metadata item is pulled in via cross-domain closure; requires the metadatamapping
  module
* Metadata sharing (raw zip packages already built and published through the metadatasharing
  module's own UI, copied out as-is; not CSV/XML — one file per package) — requires the
  metadatasharing module
* Identifier sources (identifier type, name, description; sequential: prefix, suffix, first
  identifier base, min/max length, base character set; remote: url, user, password; pool: backing
  source, batch size, minimum size, refill with task, sequential allocation) — written as
  idgen_sequential/idgen_remote/idgen_pool CSVs with pools ordered last so backing sources load
  first; remote-source passwords are never exported in plaintext — each row carries a
  `property:idgen.remote.password.<identifier source uuid>` placeholder, and the importing server
  must define the `idgen.remote.password.<identifier source uuid>` system or OpenMRS runtime
  property (retired remote sources included — Initializer still requires the password when it
  bootstraps them); sources Initializer cannot import are skipped with a warning — custom
  identifier source types from other modules, remote sources with no user (Initializer requires
  one), pools whose backing source is missing or itself skipped — as are auto generation options
  pointing at any skipped source; reserved identifiers on a source are not exported (Initializer
  has no column for them) and are flagged with a warning; requires the idgen module (4.6+)
* Auto generation options (identifier type, location, identifier source, manual entry enabled,
  auto generation enabled) — the referenced identifier type, source and location are pulled in via
  cross-domain closure; requires the idgen module (4.6+)
* FHIR concept sources (concept source, url) — the referenced concept source is pulled in via
  cross-domain closure; name and description are not exported (Initializer has no columns for
  them — it sets the name from the concept source when it creates the row); rows without a
  concept source are skipped with a warning (Initializer requires that column), and when several
  rows share one concept source only one is exported, preferring the unretired row, with a
  warning for the rest (Initializer matches rows by concept source, so duplicates would collapse
  unpredictably on import); requires the fhir2 module (1.6+)
* FHIR patient identifier systems (patient identifier type, url) — the referenced patient
  identifier type is pulled in via cross-domain closure; name and description are not exported
  (Initializer has no columns for them — it overwrites the name with the identifier type's name
  on import); rows without an identifier type are skipped with a warning (Initializer requires
  that column), and when several rows share one identifier type only one is exported, preferring
  the unretired row, with a warning for the rest (Initializer matches rows by identifier type,
  so duplicates would collapse unpredictably on import); requires the fhir2 module (1.6+)
* Address hierarchy (the `addressConfiguration.xml`, rebuilt from the ordered hierarchy levels and
  the live address template, plus a headerless `addresshierarchy.csv` of one root-to-leaf path per
  leaf entry; not CSV/XML rows — a whole-config directory) — requires the addresshierarchy module
* Cohort types (name, description) — requires the cohort module (3.5+)
* Cohort attribute types (name, description, datatype classname, preferred handler classname,
  handler config, min/max occurs) — retired attribute types are not exported (Initializer's parser
  cannot resolve them: its lookups exclude retired rows, so re-importing one fails on the name/uuid
  constraints), and datatype config is not exported (Initializer has no column for it); requires
  the cohort module (3.5+)
* System tasks (name, title, description, priority, default assignee role, rationale) — the
  default assignee is written as the provider role's uuid and returned as a cross-domain
  dependency, but provider roles themselves are not yet exported: Initializer's `providerroles`
  domain still targets the providermanagement module's provider roles, while core 2.8+ has its own
  (see [Initializer #303](https://github.com/mekomsolutions/openmrs-module-initializer/issues/303)),
  so until that lands Initializer resolves the assignee column through the providermanagement
  module only: when that module is absent the assignee is dropped with a warning, and when it is
  present a providermanagement provider role with the same uuid must already exist on the importing
  server or the row fails to import (core's own `provider_role` table is not consulted); a task
  whose assignee role no longer exists on the exporting server is exported without the assignee
  column, with a warning; requires the tasks module (1.0+)

Domains contributed by other modules (supportable, but depend on the module being present;
not yet covered):

* Forms (Bahmni forms, AMPATH forms, AMPATH form translations, HTML forms)
* Billing / cashier (billable services, payment modes, cash points, cashier item prices)
* Appointment scheduling (specialities, service definitions, service types)
* Queues
* Data filter mappings

Non-exportable Initializer domains (Liquibase, JSON key-values, OCL, Dispositions) are
out of scope.

How it works
------------
On module startup the activator runs an export on a daemon thread (so it has full read access and
does not block startup). It writes to:

    <OpenMRS application data directory>/metadata_export/configuration/<domain>/...

The export is built in two separated stages:

1. Selection. Starting from seed objects, a `Selector` walks each object's dependencies to a
   fixpoint, producing an `ExportManifest` (the set of objects to export, bucketed by domain). This
   is what makes a package self-contained: for example, exporting a concept set also pulls in its
   members.
2. Export. Each `DomainExporter` writes its bucket in its own format. The service holds a registry
   of these and contains no per-domain logic.

Export packages (REST)
----------------------
Besides the export-everything-on-startup behaviour, named *export packages* can be defined and
built over REST. A package describes what to export — a list of entries, each an Initializer
domain optionally narrowed to specific item uuids (empty list = the whole domain) — so e.g. a
"Site A locations" package exports just one site's locations (plus dependency closure). A
package with *no entries at all* exports every registered domain; `GET /domains` lists which
domains are registered on the server. Package
definitions are stored in the database; every build of a package gets an incrementing version, a
status (`QUEUED` → `RUNNING` → `COMPLETED`/`FAILED`), and a downloadable zip containing the
`configuration/` tree plus a `package.json` manifest recording exactly what was exported.

Builds run asynchronously on a daemon thread; trigger, then poll. Endpoints (all under
`/openmrs/ws/rest/v1/metadataexport`, plain Spring controllers — the webservices.rest module is
not required, but its authentication filter covers these URLs when it is installed):

| Method | Path                         | Action                                     |
|--------|------------------------------|--------------------------------------------|
| GET    | `/domains`                   | list the registered, exportable domains    |
| GET    | `/packages?includeRetired=`  | list packages                              |
| POST   | `/packages`                  | create a package (201)                     |
| GET    | `/packages/{uuid}`           | fetch one, incl. its latest build          |
| PUT    | `/packages/{uuid}`           | update name/description/entries            |
| DELETE | `/packages/{uuid}?reason=`   | retire (204)                               |
| POST   | `/packages/{uuid}/builds`    | trigger a build (202; 409 if one is active)|
| GET    | `/packages/{uuid}/builds`    | build history, newest first                |
| GET    | `/builds/{uuid}`             | poll status, incl. manifest when done      |
| GET    | `/builds/{uuid}/download`    | the zip (409 unless COMPLETED, 410 if gone)|

Example flow:

```bash
# define a package scoped to two locations
curl -u admin:pw -H 'Content-Type: application/json' -d '{
  "name": "Site A locations",
  "description": "Everything Site A needs",
  "entries": [ { "domain": "LOCATIONS", "itemUuids": ["<uuid-1>", "<uuid-2>"] } ]
}' http://localhost:8080/openmrs/ws/rest/v1/metadataexport/packages

# trigger a build, poll until COMPLETED, then download
curl -u admin:pw -X POST .../packages/<pkg-uuid>/builds
curl -u admin:pw .../builds/<build-uuid>
curl -u admin:pw -OJ .../builds/<build-uuid>/download
```

Reads require the `Get Metadata Export Packages` privilege; creating, updating, retiring,
triggering and downloading require `Manage Metadata Export Packages`. The curl examples use
basic auth, which is provided by webservices.rest's filter — without that module, authenticate
with a session instead.

If the server restarts mid-build, the activator marks any stranded QUEUED/RUNNING builds as
FAILED on startup so they never block future builds of their package.

Zips and their unzipped working copies accumulate under
`<app data dir>/metadataexport/packages/<package-uuid>/<version>/` — there is no retention policy
yet, so clean up old builds manually if disk space matters. Note also that these endpoints are
session-authenticated but not CSRF-protected (nothing under `/ws/*` is); treat them as an
admin-only API.

Requirements
------------
The Initializer module must be installed (declared in `config.xml` `require_modules`); this module
reuses its `Domain` and CSV header definitions.

Adding a new domain
-------------------
Supporting a new metadata type is a new class plus one line in the registry, never a new method on
the service.

1. Write a `DomainExporter` and annotate it `@Component` so it is discovered automatically. For a
   CSV domain, extend `CsvDomainExporter<T>`:

```java
@Component
public class EncounterTypeDomainExporter extends CsvDomainExporter<EncounterType> {

    public Domain getDomain()               { return Domain.ENCOUNTER_TYPES; }

    public boolean handles(OpenmrsObject o) { return o instanceof EncounterType; }

    public Collection<EncounterType> getAllInstances() {
        return Context.getEncounterService().getAllEncounterTypes();
    }

    // Objects from OTHER domains this one references, for cross-domain closure. Empty if none.
    public Collection<? extends OpenmrsObject> getDependencies(EncounterType t) {
        return Collections.emptyList();
    }

    protected List<BaseLineExporter<EncounterType>> chain() {
        return Arrays.asList(new EncounterTypeLineExporter());
    }

    protected String fileName() { return "encounterTypes.csv"; }
}
```

2. Write the line exporter(s). Each writes header to value pairs into an `ExportLine`; it is the
   inverse of Initializer's matching `BaseLineProcessor.fill(...)`. Reuse Initializer's header
   constants where they are `public` so the two sides cannot drift. For the primary exporter of a
   domain, extend `MetadataLineExporter<T>`: it writes the uuid and the `void/retire` short-circuit
   for you, so `export` only handles the live, domain-specific columns:

```java
public class EncounterTypeLineExporter extends MetadataLineExporter<EncounterType> {
    public void export(EncounterType t, ExportLine line) {
        line.put(BaseLineProcessor.HEADER_NAME, t.getName());
        line.put(BaseLineProcessor.HEADER_DESC, t.getDescription());
    }
}
```

Exporters that only contribute extra columns to an existing row (not the primary one) extend
`BaseLineExporter<T>` directly instead.

A CSV domain may emit more than one file by overriding `partition(instances)` (the default is one
file). When the files must load in a set sequence — e.g. idgen pools after the sources they
reference — also override `order(fileName)` to stamp each file with an Initializer `_order:`
header.

For an XML domain (Initializer loads some domains, such as global properties, from XML rather than
CSV), extend `XmlDomainExporter<T>` instead of `CsvDomainExporter<T>`. Build the DOM in
`toDocuments(instances)` — keyed by file name so a domain can emit one file or many — and use the
inherited `newDocument()` to get a `Document` without JAXP boilerplate; the base handles indentation,
encoding, and placement under `configuration/<domain>/`:

```java
@Component
public class GlobalPropertyDomainExporter extends XmlDomainExporter<GlobalProperty> {

    public Domain getDomain()               { return Domain.GLOBAL_PROPERTIES; }

    public boolean handles(OpenmrsObject o) { return o instanceof GlobalProperty; }

    public Collection<GlobalProperty> getAllInstances() {
        return Context.getAdministrationService().getAllGlobalProperties();
    }

    // Objects from OTHER domains this one references, for cross-domain closure. Empty if none.
    public Collection<? extends OpenmrsObject> getDependencies(GlobalProperty gp) {
        return Collections.emptyList();
    }

    protected String fileName() { return "globalProperties.xml"; }

    protected Map<String, Document> toDocuments(Collection<GlobalProperty> gps) {
        Document doc = newDocument();
        // build <config><globalProperties><globalProperty>... and return
        return Collections.singletonMap(fileName(), doc);
    }
}
```

Any other non-CSV, non-XML domain (for example forms as JSON) skips both base classes and implements
`DomainExporter` directly, writing whatever files it likes in `export(bucket, context)`.

That is all. Because the exporter is a `@Component`, it is registered automatically; there is no
list to edit. Selection, closure, routing, and writing are handled by the framework.

Known limitations
-----------------
* Concept description UUIDs and index-term names are not round-trip-able (Initializer
  format/loader limitations), so they are not preserved or re-loadable.
* The startup export always exports all instances of the registered domains; instance-level
  selection is available through export packages (see "Export packages (REST)").
* Cross-domain closure only pulls in objects whose domain has a registered exporter.

Building from source
--------------------
Java 8+ and Maven. `mvn clean package` produces `omod/target/metadataexport-*.omod`. Code
formatting is handled by Spotless during the build (`mvn spotless:apply` to format manually).

Installation
------------
Build the `.omod`, then either upload it via Administration > Manage Modules or drop it into the
OpenMRS application data directory's `modules/` folder and restart. Ensure the Initializer module
is also installed.
