final class ListIncomingFixture {
    void handle() {
        int count = this.packet.readInt();
        for (int i = 0; i < count; i++) {
            int id = this.packet.readInt();
            String name = this.packet.readString();
        }
        boolean confirmed = this.packet.readBoolean();
    }

    void shortCount() {
        int count = this.packet.readShort();
        for (int i = 0; i < count; i++) {
            int id = this.packet.readInt();
        }
    }

    void nested() {
        int rows = this.packet.readInt();
        for (int row = 0; row < rows; row++) {
            String name = this.packet.readString();
            int cells = this.packet.readInt();
            for (int cell = 0; cell < cells; cell++) {
                int value = this.packet.readInt();
            }
        }
    }

    void decrementing() {
        int count = this.packet.readInt();
        while (count-- > 0) {
            int id = this.packet.readInt();
        }
    }

    void countUsedBefore() {
        int count = this.packet.readInt();
        if (count > 100) return;
        for (int i = 0; i < count; i++) {
            int id = this.packet.readInt();
            String name = this.packet.readString();
        }
        boolean confirmed = this.packet.readBoolean();
    }

    void countFromField() {
        int count = this.limit;
        for (int i = 0; i < count; i++) {
            int id = this.packet.readInt();
        }
    }

    void conditionalRead() {
        int count = this.packet.readInt();
        for (int i = 0; i < count; i++) {
            int id = this.packet.readInt();
            if (id > 0) {
                String name = this.packet.readString();
            }
        }
    }

    void conditionalItemRead() {
        int count = this.packet.readInt();
        for (int i = 0; i < count; i++) {
            int id = this.packet.readInt();
            boolean flag = this.packet.bytesAvailable() > 0 ? this.packet.readBoolean() : false;
        }
    }

    void unknownRead() {
        int id = this.packet.readInt();
        double ratio = this.packet.readDouble();
    }

    void countChangedInBody() {
        int count = this.packet.readInt();
        for (int i = 0; i < count; i++) {
            int id = this.packet.readInt();
            count--;
        }
    }

    void guardThatReads() {
        int count = this.packet.readInt();
        if (this.packet.readBoolean()) return;
        for (int i = 0; i < count; i++) {
            int id = this.packet.readInt();
        }
    }
}
