package teaforge.platform.RoboRio

sealed class Error {
    data class FileNotFound(
        val path: String,
    ) : Error()

    data class FileAccessDenied(
        val path: String,
        val reason: String,
    ) : Error()

    data class InvalidPath(
        val path: String,
        val reason: String,
    ) : Error()

    data class FileReadError(
        val path: String,
        val reason: String,
    ) : Error()
}