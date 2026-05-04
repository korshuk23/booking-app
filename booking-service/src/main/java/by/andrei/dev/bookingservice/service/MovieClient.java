package by.andrei.dev.bookingservice.service;

import com.korshuk.dev.core.grpc.MovieRequest;
import com.korshuk.dev.core.grpc.MovieResponse;
import com.korshuk.dev.core.grpc.MovieServiceGrpc;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class MovieClient {

    @GrpcClient("movie-service")
    private MovieServiceGrpc.MovieServiceBlockingStub movieStub;

    public MovieResponse getMovieInfo(UUID movieId) {
        try {
            MovieRequest request = MovieRequest.newBuilder()
                    .setMovieId(movieId.toString())
                    .build();
            log.info("Calling Movie Service for ID: {}", movieId);
            return movieStub.getMovieInfo(request);
        } catch (Exception e) {
            log.error("Error while calling Movie Service for ID: {}", movieId, e);
            throw new RuntimeException("Movie Service is unavailable");
        }
    }
}
