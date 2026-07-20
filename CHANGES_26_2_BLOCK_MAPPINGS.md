# Dynmap 26.2 block mapping fixes — changelog

**Context:** On Minecraft 26.2 (Paper), a number of blocks rendered as solid black on the Dynmap
web map, with no error at all in the server log. Root cause: Dynmap silently renders any block
state it has no `texture_1.txt` / `models_1.txt` mapping for as a blank/black tile — there is no
warning when a block is simply *missing* from the mapping data. This mostly affects blocks added
in Minecraft versions newer than the last time these two files were updated (last version tag
present before this fix: `[1.21.9-]`).

Method used: diffed `assets/minecraft/blockstates/*.json` between the Minecraft 1.21.7, 1.21.11,
and 26.2 client jars to get the exact list of new/changed blocks, then cross-checked every
existing mapped block's real state properties against the current 26.2 blockstate JSON to catch
silent property renames (not just brand-new blocks).

## Files changed

- `DynmapCore/src/main/resources/texture_1.txt`
- `DynmapCore/src/main/resources/models_1.txt`
- `DynmapCore/src/main/resources/texturepacks/standard/assets/minecraft/textures/block/*.png` (new texture files, extracted from the vanilla 26.2 client jar — Mojang assets, same as every other vanilla texture already bundled in this resource pack)

All new entries are tagged `[26.2-]` so they only apply on 26.2+ servers and cannot affect older versions.

## 1. New blocks added (introduced between 1.21.7 and 26.2), fully mapped

**Sulfur family** — full block, bricks, chiseled, polished, potent variant, plus stairs/slabs/walls
for the plain, brick, and polished forms (mapped the same way the existing `tuff`/`tuff_bricks`
family already was):
`sulfur`, `sulfur_bricks`, `chiseled_sulfur`, `polished_sulfur`, `potent_sulfur`,
`sulfur_stairs`, `sulfur_slab`, `sulfur_wall`,
`sulfur_brick_stairs`, `sulfur_brick_slab`, `sulfur_brick_wall`,
`polished_sulfur_stairs`, `polished_sulfur_slab`, `polished_sulfur_wall`

**Cinnabar family** — same treatment as sulfur:
`cinnabar`, `cinnabar_bricks`, `chiseled_cinnabar`, `polished_cinnabar`,
`cinnabar_stairs`, `cinnabar_slab`, `cinnabar_wall`,
`cinnabar_brick_stairs`, `cinnabar_brick_slab`, `cinnabar_brick_wall`,
`polished_cinnabar_stairs`, `polished_cinnabar_slab`, `polished_cinnabar_wall`

**Sulfur Spike** — `sulfur_spike` (10 model states: base/frustum/middle/tip/tip_merge × up/down).
Mapped as a billboard cross patch, the same technique Dynmap already uses for vanilla
`pointed_dripstone` (added a `sulfur_spike` line right next to the existing `pointed_dripstone`
patchblock definition in `models_1.txt`) — this is Dynmap's accepted approximation for this shape,
not a compromise specific to this fix.

**Flowers:**
`golden_dandelion` (mapped identically to the existing `dandelion` cross-plant patchblock) and
`potted_golden_dandelion` (mapped identically to `potted_dandelion`).

**Copper Golem Statue** (8 oxidation/wax variants: `copper_golem_statue`, `exposed_copper_golem_statue`,
`weathered_copper_golem_statue`, `oxidized_copper_golem_statue`, and their `waxed_*` counterparts):
this block has **no real block model** in vanilla — it's drawn entirely through a
BlockEntityRenderer, so there is no faithful patch-based geometry to map. Approximated as a plain
solid cube using the matching existing copper-oxidation-stage texture (`copper_block` /
`exposed_copper` / `weathered_copper` / `oxidized_copper`) so it at least shows up as
copper-colored on the map instead of invisible. **Flagged for anyone reviewing this: a real fix
would need a custom Java `CustomRenderer` class to approximate its actual statue shape.**

The following blocks from the same 1.21.7→26.2 diff were checked and found **already correctly
mapped** before this fix (no changes needed): all wood `*_shelf` blocks (acacia, bamboo, birch,
cherry, crimson, dark_oak, jungle, mangrove, oak, pale_oak, spruce, warped), and the full copper
decor set (chains, bars, torches, wall torches, lanterns, lightning rods, chests — including all
`waxed_*` variants).

## 2. Bug fix: `creaking_heart` blockstate property renamed

`creaking_heart` was already mapped (added at `[1.21.4-]`), but only for its **old** state property
`active:true/false`. At some point before 26.2, Mojang renamed/expanded this to a 3-value property
`creaking_heart_state:awake/dormant/uprooted`. Since the property name itself changed, every
`creaking_heart` on a 26.2 server matched *zero* mapping entries and rendered black.

Fix: added 9 new `[26.2-]` mapping lines (3 axis × 3 `creaking_heart_state` values) using texture
files that were already bundled in the resource pack but never wired up
(`creaking_heart_awake`/`_dormant` + their `_top` variants). The old `[1.21.4-]` `active:true/false`
entries were **left in place untouched** — they're harmless dead weight on 26.2 (no block state
there has an `active` property anymore) but still needed for correctness on 1.21.4–1.21.11 servers.

## 3. Verification performed (no other issues found)

- Extracted **every** `assets/minecraft/blockstates/*.json` from the 26.2 client jar and
  cross-referenced all ~550 distinct `(blockId, stateProperty)` pairs currently referenced anywhere
  in `texture_1.txt`/`models_1.txt` against the real property names for that block in 26.2 (handling
  both the flat `"axis=x,type=y"` variant-key format and the newer `"multipart"`/`"when"` format).
  Only flagged mismatch: the intentionally-kept legacy `creaking_heart`/`active` entries described
  above — nothing else.
- Confirmed all 92 blocks from the 1.21.7→26.2 blockstate diff are now covered.

## 4. Investigated, not a real bug (self-resolved)

A `copper_chest[facing=north,type=single,waterlogged=true] - not enough textures for faces (16 > 6)`
severe log line was observed once. Traced it as far as `TexturePack.java`'s integrity check
(`HDBlockStateTextureMap` / `HDBlockModels.getNeededTextureCount`) and `ChestStateRenderer.java`;
added temporary debug logging to investigate further, but on the next clean server restart (plus a
`/dynmap purgemap` + `radiusrender`) the error did not reproduce at all and the copper chest
rendered correctly. Likely a one-off load-order artifact at plugin boot. Debug logging was removed
again afterward — no net code change here, just noting it in case it resurfaces for someone else.

## Not covered / left as follow-up

- **Copper Golem Statue** real geometry (currently a flat-color cube approximation, see above).
- No other known gaps as of this writing (26.2, 2026-07-19).
