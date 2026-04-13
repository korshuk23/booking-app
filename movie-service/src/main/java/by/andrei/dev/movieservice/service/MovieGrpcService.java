package by.andrei.dev.movieservice.service;

import by.andrei.dev.movieservice.entity.Movie;
import by.andrei.dev.movieservice.repository.MovieRepository;
import com.korshuk.dev.core.grpc.MovieRequest;
import com.korshuk.dev.core.grpc.MovieResponse;
import com.korshuk.dev.core.grpc.MovieServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class MovieGrpcService extends MovieServiceGrpc.MovieServiceImplBase {
    private final MovieRepository movieRepository;

    @Override
    public void getMovieInfo(MovieRequest request, StreamObserver<MovieResponse> responseObserver) {
        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(()-> new RuntimeException("Movie not found: " +request.getMovieId()));
        MovieResponse response = MovieResponse.newBuilder()
                .setMovieId(movie.getId())
                .setTitle(movie.getTitle())
                .setPrice(movie.getPrice())
                .setIsAvailable(movie.getIsAvailable())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
