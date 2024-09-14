package com.task10;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
@Data
public class TableResponse {
    public TableResponse(final List<Table> tables) {
        this.tables = tables;
    }

    private List<Table> tables;
}
