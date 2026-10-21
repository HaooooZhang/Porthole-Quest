package ink.myumoon.portholequest.model;

import java.util.List;

public final class QuestDescriptionSnapshotCheck {
    public static void main(String[] args) {
        require(!QuestLogSnapshot.descriptionNeedsDialog(false, false, 9, 10), "9 visible rows stay inline");
        require(QuestLogSnapshot.descriptionNeedsDialog(false, false, 10, 10), "10 visible rows open the dialog");
        require(!QuestLogSnapshot.descriptionNeedsDialog(false, false, 10, 11), "dialog threshold is configurable");
        require(QuestLogSnapshot.descriptionNeedsDialog(true, false, 1, 10), "images open the dialog");
        require(QuestLogSnapshot.descriptionNeedsDialog(false, true, 1, 10), "pagination opens the dialog");

        var first = QuestLogSnapshot.DescriptionPart.text("first");
        var image = QuestLogSnapshot.DescriptionPart.image("{}", 32, 16, "CENTER", false);
        var last = QuestLogSnapshot.DescriptionPart.text("last");
        var description = new QuestLogSnapshot.Description(List.of(
                new QuestLogSnapshot.DescriptionPage(List.of(first, image)),
                new QuestLogSnapshot.DescriptionPage(List.of(last))));
        require(description.hasImages(), "image metadata survives in detached snapshot");
        var overview = description.overviewParts();
        require(overview.size() == 3 && overview.get(0).equals(first) && overview.get(2).equals(last),
                "overview flattens page text in source order, excludes images, and separates pages");
        require(overview.get(1).componentJson().equals("\"\\n\""),
                "flattened pages retain a text line boundary");

        var imageBetweenText = new QuestLogSnapshot.Description(List.of(new QuestLogSnapshot.DescriptionPage(List.of(
                first,
                QuestLogSnapshot.DescriptionPart.image("{}", 100, 100, "CENTER", false),
                last))));
        var imageBetweenTextOverview = imageBetweenText.overviewParts();
        require(imageBetweenTextOverview.size() == 3
                        && imageBetweenTextOverview.get(0).equals(first)
                        && imageBetweenTextOverview.get(1).componentJson().equals("\"\\n\"")
                        && imageBetweenTextOverview.get(2).equals(last),
                "hidden image rows retain a line break between surrounding text");

        var blankLineDescription = new QuestLogSnapshot.Description(List.of(new QuestLogSnapshot.DescriptionPage(List.of(
                QuestLogSnapshot.DescriptionPart.text("\"before\""),
                QuestLogSnapshot.DescriptionPart.text("\"\\n\""),
                QuestLogSnapshot.DescriptionPart.text("\"\""),
                QuestLogSnapshot.DescriptionPart.text("\"\\n\""),
                QuestLogSnapshot.DescriptionPart.text("\"after\"")))));
        var blankLineOverview = blankLineDescription.overviewParts();
        require(blankLineOverview.size() == 5
                        && blankLineOverview.get(1).componentJson().equals("\"\\n\"")
                        && blankLineOverview.get(3).componentJson().equals("\"\\n\""),
                "empty source rows retain both surrounding line boundaries");

        boolean immutable = false;
        try {
            description.overviewParts().add(first);
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        require(immutable, "overview text is immutable");
        System.out.println("Quest description snapshot checks PASS: threshold, image, page flattening, immutability");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
