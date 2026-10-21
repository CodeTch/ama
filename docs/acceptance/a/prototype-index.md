# A-05 prototype reference index

Source: tracked `designDocs/智能会议助手1021版.html`, SHA-256 `ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`. This index describes the **final render after all override scripts and render() initialization**, not the early page definitions. The original is unchanged. Reference demonstration employees/meetings are original test data, never runtime fixtures.

## Fixed capture environment

See [provenance.json](prototype/provenance.json): Ubuntu 24.04, Playwright 1.58.2, Chromium/headless-shell revision 1208 / 145.0.7632.6, scale 1, zh-CN, Asia/Shanghai, light color scheme, reduced motion. Browser revision is pinned transitively by package-lock.json. Font packages: fonts-noto-cjk 1:20230817+repack1-3, fonts-liberation 1:2.1.5-3, fonts-dejavu-core 2.37-8. CJK regular/bold font hashes are recorded. Prototype and application use the same original font stack and host font fallback. The full originals preserve demonstration update timestamps; no manual data/layout mask is applied to them.

## Final UI entry inventory

| Original entry | Final UI / entry | 1440×900 reference | 1024×768 reference |
| --- | --- | --- | --- |
| `#/meetings` | Full-width 56px topbar; no sidebar; list scope/actions | [Full original](prototype/1440x900/original-meetings.png) | [Full original](prototype/1024x768/original-meetings.png) |
| List → 导入会议 | `aci-import-modal`, meeting main files + background materials | [Modal](prototype/1440x900/original-import-modal.png) | [Modal](prototype/1024x768/original-import-modal.png) |
| First long-transcript meeting name | `#/meeting/DEMO-TRANSCRIPT-LONG-20261020/transcript`, final correction workbench | [Page](prototype/1440x900/original-transcript-page.png) | [Page](prototype/1024x768/original-transcript-page.png) |
| Workbench → 编辑会议信息 | final `ac-info-drawer`, right-side information form | [Drawer](prototype/1440x900/original-info-drawer.png) | [Drawer](prototype/1024x768/original-info-drawer.png) |

The two meeting-list references are full-page images, including horizontally scrollable table containment and footer. Secondary page/dialog/drawer references show the original viewport. The drawer and workbench were actually entered using the original visible controls. Other future business pages are not claimed as implemented or exhaustively indexed by this shell task.

## Prototype-derived shared-region comparison

`web/scripts/capture-prototype.mjs` opens only the original local HTML with an isolated browser context for each viewport. It records the full originals first, then captures a 0,0,width,204 region into `web/tests/visual/baselines/{1440x900,1024x768}/meeting-shell.png`. Tests compare the Vue page against these original-derived images with zero allowed differing pixels. Never run Playwright `--update-snapshots` to accept the implementation as a reference.

The compared area contains the topbar/home navigation at x24, page heading and description, recording/import button positions, owned/joined scope row and list/group icons. The topbar-right demo/identity subtree is hidden **in both comparison captures only** because it contains demonstration guide/reset controls and a fictional signed-in employee; the runtime instead states that enterprise login is unavailable. The unmodified full original is retained, and the actual full runtime screenshots remain unmasked. This exclusion does not cover the home link, header border, title, description, actions, scope row, widths or margins.

The remainder below y204 is explicitly outside the shared shell baseline: the prototype's filters, 34 demo meeting records, pagination and demo footer have no delivered business API. The runtime shows an unavailable-data card and later-phase integration note in that area. No empty-state screenshot is represented as a prototype-derived baseline; behavior/phone-width tests cover it. This is a comparison of shared shell regions, not full-page business-flow equivalence.

Final difference inspection: the first comparison detected 66 different pixels in the layers icon at both sizes. The SVG was corrected using the final prototype icon; the original-derived PNGs were not regenerated from the implementation. Final shared-region comparisons passed with zero differing pixels. [Actual runtime evidence](implementation/) is retained for review, including the unmasked identity/not-ready region.

## Scope and replay

Delivered: Vue shell, direct `/meetings`, root redirect, unknown-route return, native modal notices, keyboard scope switching, responsive actions, unavailable-data container, cookie transport and generated current contract. Pending B/C: SSO, personnel directory, meeting queries, filters/topic management, imports, audio capture, transcription/AI/publishing and associated business forms. No future button reports operational success.

See [web/README.md](../../../web/README.md) for exact install/test commands. References require the pinned browser and recorded fonts; reference capture requires explicit `--refresh-reference` when files already exist. `npm ci` and normal tests never regenerate reference images.

Primary compatibility references used: [Vite Node requirements](https://vite.dev/guide/), [Playwright releases](https://playwright.dev/docs/release-notes), [OpenAPI TypeScript 7.x](https://openapi-ts.dev/introduction). Node 22.23.4 satisfies Vite's supported Node 22 range. Exact dependency pins and esbuild 0.28.1 override remove the audited vulnerable earlier patches; npm audit reports zero vulnerabilities for the web lockfile at delivery.
