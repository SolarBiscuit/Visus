# Licences of Visus and the libraries it bundles (2026-11-06)

| Component | Licence | Source of truth |
|---|---|---|
| Oculus 1.8.0 / Iris code (most of Visus) | LGPL-3.0-only | `mods.toml` `license="LGPL-3.0-only"`, Iris repository |
| glsl-transformer 2.0.0-pre13 (bundled, relocated) | **AGPL-3.0**. The repository `LICENSE` at tag `v2.0.0-pre13` is the GNU AGPL v3; the Maven POM says "GPL v3.0 with exceptions". | github.com/IrisShaders/glsl-transformer |
| ANTLR 4 runtime 4.11.1 | BSD-3-Clause | |
| JCPP 1.4.14 | Apache-2.0 | Maven POM |
| Ithaka Digraph, Commons Collections tries | Apache-2.0 | |
| `visus-api` (planned) | MIT, written from scratch | |

## The finding
The glsl-transformer README says:
- Software that uses the library must itself be AGPLv3.
- The exceptions: a specific noncommercial permission that douira grants to certain projects (Iris has one), or a commercial licence.

Oculus redistributes it inside an LGPL jar, presumably under Iris's permission. It is unclear whether that permission extends to further forks such as Visus.

## What this means
- **Private or modpack-internal use** (RatPack testing): no practical issue.
- **Public release** has three options:
  1. **Ask douira** to extend the noncommercial permission to Visus. Simplest, and keeps Visus LGPL.
  2. **License the combined Visus jar as AGPL-3.0.**
     - LGPL-3.0 code may be relicensed as GPL-3.0, and GPL-3.0 §13 lets it be combined with AGPL-3.0.
     - The AGPL network clause does not matter for a client mod.
     - We must publish full source, which LGPL already requires.
     - `visus-api` stays MIT, because it does not contain glsl-transformer.
  3. **Replace glsl-transformer.** That is a large rewrite of every transformer, and it conflicts with the "identical transformed GLSL" rule. Not recommended.
- **Recommendation:** option 1, with option 2 as the fallback. This is a user decision; record it in the roadmap and `NOTICE` when it is made.

## Decision (2026-11-06): option 2, AGPL-3.0-only
The user chose to license the Visus jar as AGPL-3.0-only. They need to be able to distribute it freely and earn ad
revenue from downloads, and they want everyone who builds on Visus to credit it and stay open source.

How AGPL-3.0 covers those needs:
- **Free distribution and ad revenue are allowed.** The licence does not restrict commercial use or charging. CurseForge and Modrinth rewards are fine.
  Every distributed jar must come with its Corresponding Source at no extra charge. A public repository at the release tag is enough.
  Do not put the source behind ad-gated links.
- **Forks must stay open and keep the credits.**
  - Modified versions must be AGPL-3.0, publish their source, keep the copyright and licence notices, and mark their changes (sections 4 and 5).
  - `NOTICE` adds a section 7(b) term for Visus-authored material: modified versions must also keep "Based on Visus by SolarBiscuit" visible.
- **The network clause (section 13)** applies only to software that users reach over a network. A client-side mod is not affected.

Limits:
- Mods that use only `visus-api` (MIT) can use any licence, closed ones included. This keeps Atmosphera (MIT) and third-party integrations possible.
- Mods that link to or mix into Visus internals are, on the conservative (FSF) reading, combined works. They should use an AGPL-compatible licence: MIT, LGPL, GPL or AGPL.
  - RatOptimization (MIT) and AtmospheraLib (MIT) are fine.
  - **NeoVoxy is All-Rights-Reserved and holds about 150 internal references.** Moving it to `visus-api` in 0.4.0 removes the problem.
    Until then, either release NeoVoxy under an open licence or keep the two from being published as a combined work.
- Third-party mods that already link to Oculus internals ship their own jars, and the end user combines them. Visus changes nothing for them.
- This is a practical reading, not legal advice.

Files changed in 0.1.1:
- `LICENSE` is now AGPL-3.0.
- The LGPL text moved to `COPYING.LESSER`.
- `NOTICE`.
- `mods.toml` `license="AGPL-3.0-only"`.
- All licence files are bundled in `META-INF/visus/`.
