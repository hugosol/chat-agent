package com.hugosol.chatagent.flashcard;

public enum Rating {
    AGAIN(1), HARD(2), GOOD(3), EASY(4);

    private final int pyValue;

    Rating(int pyValue) {
        this.pyValue = pyValue;
    }

    int pyValue() {
        return pyValue;
    }
}
