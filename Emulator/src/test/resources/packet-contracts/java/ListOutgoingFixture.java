final class ListOutgoingFixture {
    void composeInternal() {
        this.response.appendInt(this.rooms.size());
        for (Room room : this.rooms) {
            this.response.appendInt(room.getId());
            this.response.appendString(room.getName());
        }
        this.response.appendBoolean(true);
    }

    void twoLists() {
        this.response.appendInt(ids.size());
        for (Integer id : ids) {
            this.response.appendInt(id);
        }
        this.response.appendInt(names.size());
        for (String name : names) {
            this.response.appendString(name);
        }
    }

    void nested() {
        this.response.appendInt(rows.size());
        for (Row row : rows) {
            this.response.appendString(row.name());
            this.response.appendInt(row.cells().size());
            for (Integer cell : row.cells()) {
                this.response.appendInt(cell);
            }
        }
    }

    void shortCount() {
        this.response.appendShort(ids.size());
        for (Integer id : ids) {
            this.response.appendInt(id);
        }
    }

    void indexed() {
        this.response.appendInt(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            this.response.appendInt(ids.get(i));
        }
    }

    void helperBody() {
        this.response.appendInt(rooms.size());
        for (Room room : rooms) {
            appendRoom(room);
        }
    }

    private void appendRoom(Room room) {
        this.response.appendInt(room.getId());
        this.response.appendString(room.getName());
    }

    void countMismatch() {
        this.response.appendInt(ids.size() - 1);
        for (Integer id : ids) {
            this.response.appendInt(id);
        }
    }

    void otherIterable() {
        this.response.appendInt(ids.size());
        for (Integer id : others) {
            this.response.appendInt(id);
        }
    }

    void conditionalBody() {
        this.response.appendInt(rooms.size());
        for (Room room : rooms) {
            if (room.isVisible()) {
                this.response.appendInt(room.getId());
            }
        }
    }

    void breakBody() {
        this.response.appendInt(ids.size());
        for (Integer id : ids) {
            if (id < 0) break;
            this.response.appendInt(id);
        }
    }

    void emptyItem() {
        this.response.appendInt(rooms.size());
        for (Room room : rooms) {
            room.touch();
        }
    }

    void detachedCounter() {
        this.response.appendInt(ids.size());
        this.response.appendString("x");
        for (Integer id : ids) {
            this.response.appendInt(id);
        }
    }
}
