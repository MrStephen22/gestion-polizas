package gestion_polizas.infrastructure.adapters.input.rest.responses;

public record ApiResponse<T>(boolean success, String message, T body) {

    public static <T> ApiResponse<T> success(T body) {
        return success("Operacion realizada correctamente", body);
    }

    public static <T> ApiResponse<T> success(String message, T body) {
        return new ApiResponse<>(true, message, body);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
