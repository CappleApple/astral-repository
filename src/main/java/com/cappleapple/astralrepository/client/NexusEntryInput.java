package com.cappleapple.astralrepository.client;

/** Keeps choosing a craft quantity separate from submitting a request. */
public final class NexusEntryInput {
    public enum Action { NONE, SELECT_CRAFT, CRAFT_STACK, PICKUP, QUICK_WITHDRAW, DEPOSIT }
    private NexusEntryInput() {}

    public static Action action(int button, boolean shift, boolean carrying, boolean craftable, long storedCount) {
        if (button < 0 || button > 2) return Action.NONE;
        if (button == 2) return craftable ? Action.SELECT_CRAFT : Action.NONE;
        if (carrying) return Action.DEPOSIT;
        if (button == 1 && shift && craftable) return Action.CRAFT_STACK;
        if (storedCount > 0) return shift ? Action.QUICK_WITHDRAW : Action.PICKUP;
        return craftable ? Action.SELECT_CRAFT : Action.NONE;
    }
}
