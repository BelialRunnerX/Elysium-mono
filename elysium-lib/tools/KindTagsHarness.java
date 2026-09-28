import com.elysium.lib.item.ElysiumGearTree;
import com.elysium.lib.item.ElysiumKindTags;

import java.util.List;

/**
 * Runs the kind-tag table on the JDK alone and fails on any fault it reports.
 *
 *   javac -cp "/tmp/elysium-classes/elysium-lib" -d /tmp/kh tools/KindTagsHarness.java
 *   java  -cp "/tmp/elysium-classes/elysium-lib:/tmp/kh" KindTagsHarness
 *
 * What it proves: every row names a kind the gear tree has, every tag is a
 * namespaced id, and every leaf of the tree that is not a curio resolves to
 * at least one vanilla tag through its own row or an ancestor's. It prints
 * the resolved table, one kind per line, for validate.py to hold to vanilla's
 * own tag files. What it cannot see: the registry walk, which needs a game;
 * the live suite's gear_kind_tags scenario asks a running server.
 */
public final class KindTagsHarness {

    private KindTagsHarness() {
    }

    public static void main(String[] args) {
        List<String> faults = ElysiumKindTags.faults();
        for (String fault : faults) {
            System.out.println("  FAIL: " + fault);
        }
        int resolved = 0;
        StringBuilder table = new StringBuilder();
        for (String kind : ElysiumGearTree.names()) {
            List<String> tags = ElysiumKindTags.tagsFor(kind);
            if (!tags.isEmpty()) {
                resolved++;
            }
            table.append("kind ").append(kind).append(" -> ").append(String.join(" ", tags)).append('\n');
        }
        // The summary first, because the validator prints a harness's first
        // line as its verdict; the table after it, for the section that
        // holds the tags to vanilla's own files.
        System.out.println("kind tags: " + ElysiumKindTags.TAGS.size() + " row(s), " + resolved + " of "
                + ElysiumGearTree.names().size() + " kind(s) resolved to " + ElysiumKindTags.allTags().size()
                + " tag(s), " + (faults.isEmpty() ? "no faults" : faults.size() + " fault(s)"));
        System.out.print(table);
        if (!faults.isEmpty()) {
            System.exit(1);
        }
    }
}
