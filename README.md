# Refined Storage - Advanced Pattern Grid (rsapg)

An Advanced Pattern Grid for [Refined Storage](https://github.com/refinedmods/refinedstorage) 2 on
NeoForge 1.21.1. It is Refined Storage's own pattern grid (same block entity, menu and screen, so
EMI/JEI transfer and other addons keep working) with these differences:

- **No blank-pattern slot.** Blank patterns come from the storage network. The spot where the slot
  was shows how many blanks the network holds.
- **Upgrade panel** (four slots, beside the grid). An **Autocrafting Upgrade** gets blank patterns
  crafted when none are stocked, and keeps one spare of each kind ready.
- **Refined Fluid Substitution:** a crafting recipe RFS can substitute a fluid in is encoded as a Fluid
  Substitution Pattern; one is made from a blank and two buckets if none are stocked.
- **Fuzzy mode** is left off a pattern it would not change.
- **Warnings** use Refined Storage's own icon on the output slot (out of blanks, out of materials,
  network full, ...), with the reason in the tooltip.
- **EMI recipe tree dump:** a "+" button in EMI's recipe tree screen (opened from an Advanced Pattern
  Grid) encodes every recipe in the tree the network can't craft yet and places each pattern:
  crafting, stonecutter and smithing patterns into an
  [rsmbac](https://github.com/wraithhawit/rsmc) structure first, then autocrafters that feed no
  machine; processing patterns into an autocrafter facing a machine EMI lists for that recipe type.
  A preview comes first; the grid's own recipe is put back afterwards. Among machines that fit, the
  lowest tier that can run the recipe wins, and a pattern-holding block (such as gttweaks' RS Pattern
  Buffer) wins a tie over an autocrafter. Other mods describe their machines through
  `com.wraithhawit.rsapg.api.TreeDumpApi`; gttweaks does this for GregTech (tiers, multiblocks fed
  through an input bus, RS Pattern Buffers).
- **"Place created patterns" side button:** with it on, the normal create button puts the new pattern
  where the dump would (processing patterns need the recipe to have come in through EMI's "+" so the
  grid knows the machine); otherwise it goes into network storage. The action bar says where.

Works with Refined Storage's autocrafter and Cable Tiers' tiered autocrafters. GregTech programmed
circuits on patterns are handled by gttweaks, not here.

## Requirements

| | |
|---|---|
| Required | Minecraft 1.21.1, NeoForge 21.1, Refined Storage 2.0.9+ |
| Optional | EMI (tree dump), Refined Fluid Substitution, rsmbac, Cable Tiers |

Install on both client and server; mismatched versions are refused at join.

## Building

Copy the jars listed in [libs/README.md](libs/README.md) into `libs/`, then `./gradlew build`.
`/rsapg selftest <grid pos>` runs the headless checks on a test rig.

## Credits

Textures: the block faces were designed by Astra in the style of Cable Tiers; the screen overlay is
Refined Storage's. Refined Storage, Refined Fluid Substitution and Cable Tiers are MIT-licensed; no
code from them is included.

All Rights Reserved.
