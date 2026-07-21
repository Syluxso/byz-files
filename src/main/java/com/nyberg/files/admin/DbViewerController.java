package com.nyberg.files.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/db")
public class DbViewerController {

    private static final Logger log = LoggerFactory.getLogger(DbViewerController.class);
    private static final String SCHEMA = "files";

    private final JdbcTemplate jdbc;
    private final AdminAccess adminAccess;

    public DbViewerController(JdbcTemplate jdbc, AdminAccess adminAccess) {
        this.jdbc = jdbc;
        this.adminAccess = adminAccess;
    }

    @GetMapping("/tables")
    public List<String> tables() {
        adminAccess.requirePlatformAdmin();
        return listTableNames();
    }

    @GetMapping("/tables/{table}")
    public Map<String, Object> tableData(
            @PathVariable String table,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        adminAccess.requirePlatformAdmin();
        try {
            List<String> valid = listTableNames();
            if (!valid.contains(table)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown table");
            }
            if (!table.matches("[a-zA-Z0-9_]+")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid table name");
            }

            String quoted = "\"" + SCHEMA + "\".\"" + table + "\"";
            int offset = Math.max(0, page) * Math.max(1, size);
            int limit = Math.min(Math.max(1, size), 500);

            List<String> columns = jdbc.query(
                    "SELECT column_name::text FROM information_schema.columns " +
                    "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position",
                    (rs, rowNum) -> rs.getString(1),
                    SCHEMA, table);

            if (columns.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Table has no columns");
            }
            String orderCol = columns.contains("created_at") ? "created_at"
                    : columns.contains("id") ? "id"
                    : columns.get(0);
            String orderQuoted = "\"" + orderCol + "\"";

            List<Map<String, Object>> rawRows = jdbc.queryForList(
                    "SELECT * FROM " + quoted + " ORDER BY " + orderQuoted + " DESC NULLS LAST LIMIT ? OFFSET ?",
                    limit, offset);

            List<List<String>> rows = new ArrayList<>();
            for (Map<String, Object> row : rawRows) {
                List<String> cells = new ArrayList<>();
                for (String col : columns) {
                    Object val = row.get(col);
                    if (val == null) {
                        val = row.get(col.toLowerCase());
                    }
                    cells.add(val != null ? val.toString() : null);
                }
                rows.add(cells);
            }

            Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + quoted, Long.class);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("columns", columns);
            result.put("rows", rows);
            result.put("total", total != null ? total : 0L);
            result.put("page", page);
            result.put("size", size);
            return result;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("admin db tableData({}) failed: {}", table, ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "tableData failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private List<String> listTableNames() {
        try {
            return jdbc.query(
                    "SELECT table_name::text FROM information_schema.tables " +
                    "WHERE table_schema = ? AND table_type = 'BASE TABLE' " +
                    "ORDER BY 1",
                    (rs, rowNum) -> rs.getString(1),
                    SCHEMA);
        } catch (Exception ex) {
            log.error("admin db tables failed: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "tables failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }
}
