# AEM NodeMorph Tool

Transform your Adobe Experience Manager (AEM) content management with the **AEM NodeMorph Tool**— a powerful, intuitive utility designed to streamline bulk node operations directly within the AEM environment. Whether you’re searching for specific nodes or executing complex updates across your JCR repository, NodeMorph empowers administrators and developers with precision, flexibility, and ease. Built with AEM’s Coral UI and backed by a robust OSGi service, this tool is your go-to solution for managing content at scale.

## Key Features

- **Search with Precision:** Query nodes by path, property, or name with flexible filters and verbose output options.
- **Bulk Updates Made Simple:** Add, delete, replace, or copy properties and nodes across your repository with a single click.
- **User-Friendly Interface:** Leverage AEM’s path browser, help tooltips, and dynamic forms for a seamless experience.
- **Safe Previews:** Test changes with dry-run mode before committing to the JCR.
- **Exportable Results:** Download search results as CSV for reporting or analysis.

## Background

The AEM NodeMorph Tool builds on the foundation of the [AEM Page Tool](https://github.com/gkaczmarczyk/aem-page-tool), a command-line utility for managing AEM pages. Inspired by its simplicity and power, NodeMorph brings those capabilities into a modern, web-based interface—enhancing usability with a graphical UI while expanding functionality for bulk node operations.

## Getting Started

Deploy the tool to your AEM instance, navigate to the admin interface, and dive into two powerful tabs: **Search** and **Update**. Below, we’ll walk you through each tab’s capabilities.

---

## Search Tab

The **Search Tab** is your window into the JCR repository, offering a fast, flexible way to locate nodes based on your criteria. Whether you’re auditing content, troubleshooting, or preparing for updates, this tab delivers actionable insights with minimal effort.

![Search tab UI](/imgs/search_tab.png)

### Capabilities

- **Path-Based Search:** Start with a JCR path (e.g., `/content/my-site`) using the integrated path browser. The tool searches all nodes beneath this root, giving you a comprehensive view of your content structure.
- **Property Filtering:** Narrow results by matching a property name (e.g., `sling:resourceType`) to a specific value—perfect for finding nodes with particular characteristics.
- **Node Name Queries:** Use wildcards (e.g., `mynode_*`) to pinpoint nodes by name, ideal for targeting specific structures like `jcr:content`.
- **Page Restriction:** Toggle the “Restrict to cq:Page nodes only” option to focus solely on page nodes, streamlining searches in page-heavy repositories.
- **Verbose Output:** Enable detailed results to see all properties of matched nodes, not just the basics (path, title, type).
- **Export to CSV:** Once results load, export them as a downloadable CSV file for offline analysis or documentation.

### Use Case

Imagine you need to audit all pages under `/content/we-retail/languageamasters/en` with a `jcr:primaryType` of `cq:Page`. Enter the path, check “Match Property,” specify the property and value, and hit “Search.” You’ll get a clean table of results—exportable with one click.

![Displaying search results](/imgs/search_results.png)

---

## Update Tab

The **Update Tab** is where the magic happens— bulk node manipulation at your fingertips. From adding properties to copying nodes, this tab combines power with precision, backed by a robust backend that ensures changes are applied consistently across your AEM instance.

![Update tab UI](/imgs/update_tab.png)

### Capabilities

- **Flexible Path Targeting:** Define a base path (e.g., `/content/my-site`) to scope your updates, with the path browser simplifying navigation.
- **Operation Modes:** Choose from four operations via a dropdown, each tailored to common AEM tasks:
  - **Add/Update Properties:** Set or update properties on matching nodes. Supports single values (`key=value`) or arrays (`key=[val1,val2]`), with optional filters by property or node name.
  - **Replace Properties:** Find and replace property values (e.g., swap `oldValue` for `newValue` in `jcr:title`) with pinpoint accuracy.
  - **Copy:** Move nodes or properties with three flavors:
    - **Node:** Copy a node to a new location (e.g., `node1` to `node2`).
    - **Property:** Duplicate a property within a node (e.g., `propName` to `newProp`).
    - **Property to Path:** Copy a property to a new path (e.g., `propName` to `/new/path`).
  - **Delete Properties:** Remove specified properties (e.g., `key1,key2`) from all nodes under the path—great for cleaning up outdated metadata.
  - **Create Nodes:** Add a new child node under matching parent nodes. Specify the new node name, optional primary type (defaults to `nt:unstructured`), and one or more properties to set. Supports conditional creation based on parent node properties.
- **Conditional Updates:** Filter nodes by property (`ifProp=ifValue`) or name (`jcrNodeName`) for Add/Update operations, ensuring changes hit the right targets.
- **Page-Only Mode:** Restrict updates to `cq:Page` nodes, automatically targeting their `jcr:content` subnodes for consistency with AEM conventions.
- **Dry-Run Preview:** Test your operation without committing changes—see the results table with “Pending” status to confirm your intent.
- **Detailed Results:** Post-execution, review a table of updated paths, actions taken, and statuses (e.g., “Done” or “Failed”).

### Use Case

Need to remove a legacy property (`test`) from all pages under `/content/we-retail/en/experience`? Set the path, select “Delete Properties,” enter `test` in “Property Names,” check “Restrict to cq:Page nodes only,” and run it. The tool deletes `test` from every `jcr:content` node, logging each action for review.

**Want to scaffold components under responsive grids only?**
Use the “Create Nodes” operation under `/content/we-retail/language-masters/en/experience` with a matching parent property like `sling:resourceType=wcm/foundation/components/responsivegrid`, a node name like `componentnode`, and a property like `testprop=myvalue`. The tool adds the node and sets its properties, only on matching parent nodes.

![Create Nodes Operation UI](/imgs/update_create-node.png)

---

## Why NodeMorph?

The AEM NodeMorph Tool bridges the gap between manual node tweaks and complex scripts. Built on AEM’s QueryBuilder and ResourceResolver APIs, it’s fast, reliable, and extensible. The intuitive Coral UI— complete with help icons and tooltips—makes it accessible to beginners while offering the depth power users crave. Whether you’re managing a handful of pages or thousands of nodes, NodeMorph saves time and reduces errors.

### Prerequisites

To build and deploy the AEM NodeMorph Tool, ensure you have the following installed:
- **Java 11**: The tool is compiled and runs on JDK 11, aligning with AEM’s supported runtime.
- **Maven 3.9.9**: Use this version (or compatible) for dependency management and packaging. Earlier versions may work but haven’t been tested.

Download Java 11 from [Adoptium](https://adoptium.net/) or your preferred provider, and Maven from [Apache Maven](https://maven.apache.org/download.cgi). Verify with `java -version` and `mvn -version`.

### Installation

1. Build the project: `mvn clean install -PautoInstallPackage`
2. Deploy to your AEM instance (e.g., via Package Manager or CRXDE).
3. Access the tool at `/apps/aemnodemorph/admin/content/nodemorph.html`.

### Contributing

Found a bug or have a feature idea? Open an issue or submit a pull request—we’d love to collaborate!

### License

Copyright © 2025 Gregory Kaczmarczyk

[Apache License Version 2.0](LICENSE)

Apache License Version 2.0—use it, tweak it, share it. See [LICENSE](LICENSE) for full details.
