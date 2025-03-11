# AEM NodeMorph

AEM NodeMorph is a powerful administrative tool for Adobe Experience Manager (AEM), designed to streamline bulk node operations on your author instance. Whether you need to search, update, delete, replace, or copy properties and nodes, NodeMorph provides an intuitive UI and robust backend—ideal for enterprise AEM environments.

## Overview

Built with AEM best practices, NodeMorph offers:
- **Search**: Query nodes by path, property, or name with flexible filters.
- **Update**: Perform bulk operations—add/update properties, delete properties, replace values, or copy nodes/properties—with precise match conditions.
- **Author-Focused**: Lightweight and tailored for AEM author instances.

## Modules

The project includes these core modules:

- **[core](core/README.md)**: Java bundle with OSGi services and servlets powering NodeMorph’s backend (e.g., `/bin/nodemorph/search`, `/bin/nodemorph/update`).
- **[ui.apps](ui.apps/README.md)**: Contains `/apps/nodemorph`—components, templates, and clientlibs (JS/CSS) for the NodeMorph UI.
- **all**: Single content package bundling all compiled modules for easy deployment.
- **analyse**: Static analysis for AEMaaCS compatibility validation.

## How to Build

Run these Maven commands from the project root (`/path/to/aem-nodemorph`):

- **Build All**: Compile all modules:
  ```bash
  mvn clean install
  ```

- Deploy to Author: Build and install the `all` package to a local AEM instance (default: http://localhost:4502):
   ```bash
   mvn clean install -PautoInstallSinglePackage
   ```

- Deploy Bundle Only: Install just the core bundle:
   ```bash
   mvn clean install -PautoInstallBundle
  ```

- Deploy ui.apps Only: Install just the UI package from ui.apps:
   ```bash
   cd ui.apps
   mvn clean install -PautoInstallPackage
  ```

## Accessing AEM NodeMorph

- **URL:** After deployment, navigate to `/content/nodemorph/admin.html` on your AEM author instance (e.g., http://localhost:4502/content/nodemorph/admin.html).
- **Tabs:**
  - **Search:** Query nodes and export results as CSV.
  - **Update:** Execute bulk operations with dry-run previews.

## Usage Examples

### Search

- **Path:** `/content/we-retail`.
- **Match Property:** `sling:resourceType = cq:PageContent`.
- **Result:** Lists matching nodes; exportable to CSV.

### Update Operations

- **Add/Update Properties:**
  - Path: `/content/we-retail`.
  - Match Type: Property, `sling:resourceType = cq:PageContent`.
  - Properties: `test=added`.
  - Dry Run: Preview changes, then apply.
- **Delete Properties:**
  - Path: `/content/we-retail`.
  - Property Names: `test`.
- **Replace Properties:**
  - Path: `/content/we-retail`.
  - Property Name: `test`, Find: added, Replace: replaced.
- **Copy:**
  - Path: `/content/we-retail`.
  - Copy Type: Property, Source: test, Target: test_copy.

## Testing

### Manual Testing

Run these in `/content/nodemorph/admin.html`:

1. **Search:** Verify node counts and CSV export.
2. **Add/Update:** Check property addition with Property/Node matches.
3. **Delete:** Confirm property removal.
4. **Replace:** Validate value swaps.
5. **Copy:** Test node/property duplication.

_Tip:_ Use CRXDE Lite to inspect changes (e.g., `/content/we-retail/jcr:content`).

### Unit Tests

- In core:
   ```bash
   mvn clean test
  ```
- Tests OSGi services and servlet logic.

### Static Analysis

- For AEMaaCS compatibility:
   ```bash
   mvn clean install
  ```
- Runs the analyse module automatically.

## ClientLibs

The UI leverages an AEM ClientLib (`nodemorph.admin.tool`):

- **Location:** `/apps/nodemorph/clientlibs/admin-tool`.
- **Structure:**
  - `js/`: Custom JavaScript (`script.js`).
  - `css/`: Compiled LESS styles.
  - `js.txt`/`css.txt`: Define load order.
Built via Maven and deployed with `ui.apps`.

## Roadmap

- Replication: Add an option to replicate (publish) updated nodes to AEM publish instances—enhancing workflow efficiency.

## Maven Settings

For Adobe’s public repository (needed for AEM dependencies):

- Configure `~/.maven/settings.xml` per [Adobe’s guide](http://helpx.adobe.com/experience-manager/kb/SetUpTheAdobeMavenRepository.html).

## Contributing

- **Issues:** Report bugs or suggest features via [your repo’s issue tracker].
- **Code:** Fork, branch, and submit pull requests with clear descriptions.

## License

[Apache License Version 2.0](LICENSE)
