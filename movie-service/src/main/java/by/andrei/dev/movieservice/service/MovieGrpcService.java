package by.andrei.dev.movieservice.service;

import by.andrei.dev.movieservice.entity.Movie;
import by.andrei.dev.movieservice.repository.MovieRepository;
import com.korshuk.dev.core.grpc.MovieRequest;
import com.korshuk.dev.core.grpc.MovieResponse;
import com.korshuk.dev.core.grpc.MovieServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class MovieGrpcService extends MovieServiceGrpc.MovieServiceImplBase {
    private final MovieRepository movieRepository;

    @Override
    public void getMovieInfo(MovieRequest request, StreamObserver<MovieResponse> responseObserver) {
        log.info("gRPC запрос данных фильма: {}", request.getMovieId());

        try {
            UUID uuid = UUID.fromString(request.getMovieId());
            Movie movie = movieRepository.findById(uuid)
                    .orElseThrow(() -> new RuntimeException("Movie not found"));

            MovieResponse response = MovieResponse.newBuilder()
                    .setTitle(movie.getTitle())
                    .setPrice(movie.getPrice().doubleValue())
                    .setIsAvailable(movie.isAvailable())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Ошибка gRPC movie-service: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asException());
        }
    }
}
