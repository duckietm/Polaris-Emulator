package com.eu.habbo.habbohotel.soundboard;

public record SoundboardCatalogResult(Code code, int soundId) {

    public static SoundboardCatalogResult success(int soundId) {
        return new SoundboardCatalogResult(Code.SUCCESS, soundId);
    }

    public static SoundboardCatalogResult failure(Code code) {
        return new SoundboardCatalogResult(code, 0);
    }

    public boolean successful() {
        return this.code == Code.SUCCESS;
    }

    public enum Code {
        SUCCESS(0),
        FORBIDDEN(1),
        INVALID_NAME(2),
        INVALID_URL(3),
        INVALID_RANK(4),
        INVALID_ORDER(5),
        NOT_FOUND(6),
        PERSISTENCE_FAILURE(7),
        CATALOG_FULL(8);

        private final int wireCode;

        Code(int wireCode) {
            this.wireCode = wireCode;
        }

        public int wireCode() {
            return this.wireCode;
        }
    }
}
