# JPA Fetch Lens

English | [한국어](README.ko.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> Hover over a Spring Data JPA repository method to see, as a color-coded tree, how far its
> effect reaches across your entity graph — what a query **loads**, and what `save` / `delete`
> **cascade**.

![JPA Fetch Lens](docs/shot-fetch.png)

What the method name hides — N+1 risks, a `LAZY` field Hibernate loads eagerly anyway, or a
`delete` that quietly removes children — becomes visible without leaving the editor. The tree is
appended right below the method's normal Java documentation on hover.

## Features

- **Read methods → fetch tree** — the queried entity and the associations that get loaded,
  nested by what is actually fetched. Green = pulled by this query (`join fetch` /
  `@EntityGraph`), yellow = EAGER mapping, red = LAZY (proxy, potential N+1).
- **`save` → cascade tree** — associations cascaded by `PERSIST` / `MERGE` are blue and
  expanded; the rest are gray (the save stops there).
- **`delete` → cascade tree** — associations cascaded by `REMOVE` or `orphanRemoval` are orange
  and expanded, so a surprise cascading delete is visible *before* you run it.
- **Skips what loads nothing** — `count`, `exists`, and scalar / DTO projections show no tree.
  This is decided by the method's **return type**, not its name, so a `findBy…` that actually
  runs a `count` (returns `long`) is handled correctly.
- **Reads mappings and query together** — `@ManyToOne` / `@OneToOne` / `@OneToMany` /
  `@ManyToMany` (Jakarta and javax), `@Query` `join fetch` with alias resolution, and
  `@EntityGraph(attributePaths)`.
- **Flags a pitfall** — a non-owning `@OneToOne` declared `LAZY` is loaded EAGER by Hibernate,
  and is marked accordingly.
- **Back-references** to an already-loaded parent are shown without color, since navigating to
  them triggers no extra query.
- **Configurable colors** — Settings | Tools | JPA Fetch Lens.

## Installation

- **From the IDE:** Settings/Preferences → Plugins → Marketplace → search **JPA Fetch Lens** → Install.
- **Manually:** download the plugin ZIP, then Plugins → ⚙ → *Install Plugin from Disk…*

Works in IntelliJ IDEA **Community and Ultimate** — it relies on Java PSI, so no dedicated JPA
plugin is required, just a project with JPA / Spring Data JPA on the classpath.

## Usage

Hover over a repository method. For example, with:

```java
@Query("select o from Order o join fetch o.items i join fetch i.product")
List<Order> findAllWithItems();
```

```
Order
     └ N-1  Customer: customer                    (red,   LAZY)
     └ 1-N  OrderItem: items                      (green, FETCH)
          └ N-1  Order: order                     (gray,  back-reference)
          └ N-1  Product: product                 (green, FETCH)
     └ 1-1  ShippingInfo: shipping   LAZY ignored → loads EAGER   (yellow)
```

Cardinality is shown as `N-1` (@ManyToOne), `1-N` (@OneToMany), `1-1` (@OneToOne),
`N-M` (@ManyToMany).

For `save` and `delete` the tree switches to **cascade** — what gets persisted / removed along
with the entity. `save` shows the `PERSIST` / `MERGE` cascade (blue); `delete` shows `REMOVE` /
`orphanRemoval` (orange), expanded so you can see how far the cascade reaches:

![save cascade](docs/shot-save.png)

![delete cascade](docs/shot-delete.png)

`count` / `existsById` and scalar or DTO projections show no tree at all — they don't
materialize the entity graph. This is decided by the **return type**, so it is correct even for
a `@Query("select count(o) …") long findByFoo()`.

### Color legend

The color always means **"reached by this operation"**; what the operation *is* depends on the
method:

| Color | Meaning |
|-------|---------|
| 🟢 Green | Read: loaded — `join fetch` / `@EntityGraph` |
| 🟡 Yellow | Read: EAGER mapping — always loaded |
| 🔴 Red | Read: LAZY — a proxy; touching it triggers another query (N+1 risk) |
| 🔵 Blue | `save`: cascaded — `PERSIST` / `MERGE` |
| 🟠 Orange | `delete`: cascaded — `REMOVE` / `orphanRemoval` |
| ⚪ Gray | Not reached — back-reference, or an association the operation does not cascade |

Only reached associations (green, plus EAGER on reads) are expanded to their own associations;
the rest are leaves. A per-hover caption states which mode you are looking at.

> The colors above are the **defaults** — all five (LAZY / EAGER / FETCH / save cascade / delete
> cascade) are configurable under Settings | Tools | JPA Fetch Lens. The in-editor caption always
> renders your *configured* colors, so it never disagrees with the tree.

## Configuration

**Settings → Tools → JPA Fetch Lens** lets you pick all five colors — LAZY / EAGER / FETCH
(read) and Save cascade / Delete cascade. Text color adapts (black/white) to the background for
contrast.

## Limitations

- **Runtime factors are not reflected.** An entity already in the persistence context / L2 cache
  won't trigger a query even if LAZY, and `@BatchSize` / `default_batch_fetch_size` change how
  LAZY loads. Verify actual behavior with Hibernate SQL logging.
- `@Query(nativeQuery = true)` is not analyzed (it is SQL, outside the mapping).
- `@NamedEntityGraph` (by-name reference) is not yet supported — only inline `attributePaths`.
- Associations mapped on getters (property access) are not yet supported.
- `getReferenceById` / `getOne` intentionally show no tree — they return a lazy proxy and load
  nothing, even though their return type is the entity.

## Development

```bash
./gradlew runIde       # launch a sandbox IDE with the plugin
./gradlew buildPlugin  # build the distributable ZIP
```

## License

[MIT](LICENSE) © jeong-donghee
