package gestion_polizas.infrastructure.adapters.input.rest.responses;

import java.util.List;

public record PageResponse<T>(List<T> data, int currentPage, int lastPage,
                              long totalRecords, boolean hasMorePages) {
    public static final int FIRST_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageResponse {
        data = List.copyOf(data);
    }
}
