package com.dotpet.app;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.input.TransferMode;

/** Accepts explicit Explorer file drops, without sharing the clipboard or recording input. */
final class PetFileDropTarget {
    PetFileDropTarget(Node node, BooleanSupplier available, Consumer<Boolean> hover, Consumer<List<Path>> drop) {
        node.setOnDragEntered(event -> {if (available.getAsBoolean() && event.getDragboard().hasFiles()) hover.accept(true); event.consume();});
        node.setOnDragOver(event -> {
            if (available.getAsBoolean() && event.getDragboard().hasFiles()) {
                // The app owns the filesystem operation. COPY prevents the drag source from deleting originals
                // after an asynchronous operation fails; it does not make a copy of the file.
                event.acceptTransferModes(TransferMode.COPY); hover.accept(true);
            } else hover.accept(false);
            event.consume();
        });
        node.setOnDragExited(event -> {hover.accept(false); event.consume();});
        node.setOnDragDropped(event -> {
            boolean accepted = available.getAsBoolean() && event.getDragboard().hasFiles();
            List<Path> files = accepted ? event.getDragboard().getFiles().stream().map(java.io.File::toPath).toList() : List.of();
            hover.accept(false);
            if (accepted) drop.accept(files);
            event.setDropCompleted(accepted); event.consume();
        });
    }
}
