# The Extraction Ledger — turning it on

The tree ships inside the mod. One line in your own config switches it on.

## Why it is not automatic

Pufferfish's Skills only loads the categories named in its `config.json`, and a
`config.json` supplied by a mod or data pack **replaces** the one already in
use rather than adding to it. Elysium shipped one exactly once, and it deleted
every other skill tree in the instance — the skills key opened on "There
doesn't seem to be anything here".

A mod does not get to overwrite a setting shared by every pack installed beside
it. So the tree is opt-in, and the opt-in is something you can see and undo.

## The one line

Open `config/puffish_skills/config.json` in your instance. It looks like this:

```json
{
  "version": 3,
  "categories": [
    "whatever",
    "you",
    "already_had"
  ]
}
```

Add `"extraction_ledger"` to the list:

```json
{
  "version": 3,
  "categories": [
    "whatever",
    "you",
    "already_had",
    "extraction_ledger"
  ]
}
```

Restart. The order of the list is the order of the tabs, so put it wherever you
want it to appear.

## If it still does not show

Two format details could not be verified against the mod's documentation when
the tree was written, and either one would stop the category loading. Both are
a single word in `elysium-core/tools/gen_skilltree.py` followed by a regen:

| Symptom in `latest.log` | Constant | Change it to |
|---|---|---|
| A complaint about a connection | `CONNECTION_STYLE` | `"pairs"` — but this loses the four exclusive commitments, which become ordinary nodes |
| A complaint about an attribute id or operation | `ATTRIBUTE_STYLE` | `"legacy"` — the pre-1.21 `generic.` names |

Turn on `"show_warnings": true` in the same config first; without it the mod
loads what it can and says nothing about what it could not.
