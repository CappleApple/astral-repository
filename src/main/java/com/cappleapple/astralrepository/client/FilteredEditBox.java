package com.cappleapple.astralrepository.client;

import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Retains whole-value validation for numeric entry and pasted text. */
final class FilteredEditBox extends EditBox {
    private Predicate<String> filter = value -> true;
    FilteredEditBox(Font font, int x, int y, int width, int height, Component label) { super(font, x, y, width, height, label); }
    void setFilter(Predicate<String> filter) { this.filter = filter; }
    @Override public void insertText(String input) {
        String old = getValue();
        int cursor = getCursorPosition();
        super.insertText(input);
        if (!filter.test(getValue())) {
            super.setValue(old);
            setCursorPosition(cursor);
        }
    }
}
