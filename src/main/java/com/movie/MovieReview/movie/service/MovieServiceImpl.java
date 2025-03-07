package com.movie.MovieReview.movie.service;

import com.google.gson.*;
import com.movie.MovieReview.awards.dto.AwardsAllScoreDto;
import com.movie.MovieReview.awards.entity.AwardsEntity;
import com.movie.MovieReview.awards.repository.AwardsRepository;
import com.movie.MovieReview.movie.dto.*;
import com.movie.MovieReview.movie.entity.MovieDetailEntity;
import com.movie.MovieReview.movie.entity.TopRatedMovieIdEntity;
import com.movie.MovieReview.movie.repository.MovieRepository;
import com.movie.MovieReview.movie.repository.TopRatedMovieIdRepository;
import com.movie.MovieReview.review.dto.PageRequestDto;
import com.movie.MovieReview.review.dto.PageResponseDto;
import com.movie.MovieReview.review.entity.ReviewEntity;
import com.movie.MovieReview.review.repository.ReviewRepository;
import com.movie.MovieReview.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Log4j2
@Service
public class MovieServiceImpl implements  MovieService{
    private final MovieRepository movieRepository;
    private final TopRatedMovieIdRepository topRatedMovieIdRepository;
    private final MovieRecommendService movieRecommendService;
    private final MovieCreditService movieCreditService;
    private final ReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final AwardsRepository awardsRepository;

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    private final String TMDB_API_URL = "https://api.themoviedb.org/3/movie/";
    private final String AUTH_TOKEN = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIwMjUxYmI1M2Q5YTNkMTA0NGRiYTcwZDFiMmI2ZGEwNSIsInN1YiI6IjY2MmNmNDRlZjZmZDE4MDEyODIyNGI3MCIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.yGcscHFGjYQq6B7s_OqCif9IH5jw8vlFboOuJZNKnTk";

    @Override
    public List<MovieCardDto> getTopRatedMovies(Long memberId) throws Exception {
        List<MovieCardDto> allMovies = new ArrayList<>();
        String TopRatedUrl = "top_rated?language=ko-KR&page=";

        for(int page = 1; page <= 2; page++) {
            Request request = new Request.Builder()
                    .url(TMDB_API_URL + TopRatedUrl + page + "&region=KR")
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", AUTH_TOKEN)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new Exception("Unexpected code " + response);
                }

                String jsonResponse = response.body().string();
                log.info("MovieServiceImpl: " + jsonResponse);

                JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
                JsonArray resultsArray = jsonObject.getAsJsonArray("results");

                for (JsonElement resultElement : resultsArray) {
                    JsonObject movieObject = resultElement.getAsJsonObject();

                    Long id = movieObject.get("id").getAsLong();
                    String title = movieObject.get("title").getAsString();
                    String overview = movieObject.get("overview").getAsString();
                    String posterPath = movieObject.get("poster_path").getAsString();
                    String releaseDate = movieObject.get("release_date").getAsString();

                    // 장르 ID 리스트를 String으로 변환
                    JsonArray genreIdsArray = movieObject.getAsJsonArray("genre_ids");
                    List<Integer> genreIdsList = new ArrayList<>();
                    for (JsonElement genreElement : genreIdsArray) {
                        genreIdsList.add(genreElement.getAsInt());
                    }
                    String genreIds = genreIdsList.toString();

                    // score 초기화
                    Map<String, Object> score = Map.of(
                            "avgActorSkill", 0.0,
                            "avgDirectorSkill", 0.0,
                            "avgLineSkill", 0.0,
                            "avgMusicSkill", 0.0,
                            "avgSceneSkill", 0.0,
                            "avgStorySkill", 0.0,
                            "totalAverageSkill", 0.0
                    );
                    // myScore 초기화
                    Map<String, Object> myScore = null;

                    try {
                        score = reviewService.getAverageSkillsByMovieId(id);
                        myScore = reviewService.getLatestReviewSkills(memberId, id);
                    } catch (Exception e) {
                        log.warn("Review data not found for movie ID: {}", id, e);
                        score = Map.of("avgActorSkill", 0.0, "avgDirectorSkill", 0.0, "avgLineSkill", 0.0, "avgMusicSkill", 0.0, "avgSceneSkill", 0.0, "avgStorySkill", 0.0, "totalAverageSkill", 0.0);
                        myScore = null;
//                        myScore = Map.of("actorSkill", 0, "directorSkill", 0, "lineSkill", 0, "musicSkill", 0, "sceneSkill", 0,  "storySkill", 0, "avgSkill", 0);
                    }

                    MovieCardDto movieCardDto = new MovieCardDto(id, title, overview, posterPath, releaseDate, genreIds, score, myScore);
                    allMovies.add(movieCardDto);
                }
            }
        }
        return allMovies;
    }

    @Override
    public List<MovieCardDto> getNowPlayingMovies(Long memberId) throws Exception {
        List<MovieCardDto> allMovies = new ArrayList<>();
        String NowPlayingUrl = "now_playing?language=ko-KR&page=";

        for(int page = 1; page <= 2; page++){
            Request request = new Request.Builder()
                    .url(TMDB_API_URL + NowPlayingUrl + page + "&region=KR")
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", AUTH_TOKEN)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new Exception("Unexpected code " + response);
                }

                String jsonResponse = response.body().string();
                log.info("MovieServiceImpl: " + jsonResponse);

                JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
                JsonArray resultsArray = jsonObject.getAsJsonArray("results");

                for (JsonElement resultElement : resultsArray) {
                    JsonObject movieObject = resultElement.getAsJsonObject();

                    Long id = movieObject.get("id").getAsLong();
                    String title = getJsonString(movieObject, "title");
                    String overview = getJsonString(movieObject, "overview");
                    String posterPath = getJsonString(movieObject, "poster_path");
                    String releaseDate = getJsonString(movieObject, "release_date");

                    // 장르 ID 리스트를 String으로 변환
                    JsonArray genreIdsArray = movieObject.getAsJsonArray("genre_ids");
                    List<Integer> genreIdsList = new ArrayList<>();
                    if (genreIdsArray != null) {
                        for (JsonElement genreElement : genreIdsArray) {
                            genreIdsList.add(genreElement.getAsInt());
                        }
                    }
                    String genreIds = genreIdsList.toString();

                    // score 초기화
                    Map<String, Object> score = Map.of(
                            "avgActorSkill", 0.0,
                            "avgDirectorSkill", 0.0,
                            "avgLineSkill", 0.0,
                            "avgMusicSkill", 0.0,
                            "avgSceneSkill", 0.0,
                            "avgStorySkill", 0.0,
                            "totalAverageSkill", 0.0
                    );
                    // myScore 초기화
                    Map<String, Object> myScore = null;

                    try {
                        score = reviewService.getAverageSkillsByMovieId(id);
                        myScore = reviewService.getLatestReviewSkills(memberId, id);
                    } catch (Exception e) {
                        log.warn("Review data not found for movie ID: {}", id, e);
                        score = Map.of("avgActorSkill", 0.0, "avgDirectorSkill", 0.0, "avgLineSkill", 0.0, "avgMusicSkill", 0.0, "avgSceneSkill", 0.0,  "avgStorySkill", 0.0, "totalAverageSkill", 0.0);
                        myScore = null;
//                        myScore = Map.of("actorSkill", 0, "directorSkill", 0, "lineSkill", 0, "musicSkill", 0, "sceneSkill", 0,  "storySkill", 0, "avgSkill", 0);
                    }

                    MovieCardDto movieCardDto = new MovieCardDto(id, title, overview, posterPath, releaseDate, genreIds, score, myScore);
                    allMovies.add(movieCardDto);
                }
            }
        }
        return allMovies;
    }

    // JSON 값이 null일때
    private String getJsonString(JsonObject jsonObject, String key) {
        JsonElement element = jsonObject.get(key);
        if (element != null && !element.isJsonNull()) {
            return element.getAsString();
        }
        return "";
    }


    @Override
    public List<MovieCardDto> getUpComingMovies(Long memberId) throws Exception {
        List<MovieCardDto> allMovies = new ArrayList<>();
        String NowPlayingUrl = "upcoming?language=ko-KR&page=";

        for(int page = 1; page <= 2; page++){
            Request request = new Request.Builder()
                    .url(TMDB_API_URL + NowPlayingUrl + page + "&region=KR")
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", AUTH_TOKEN )
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new Exception("Unexpected code " + response);
                }

                String jsonResponse = response.body().string();
                log.info("MovieServiceImpl: " + jsonResponse);

                JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
                JsonArray resultsArray = jsonObject.getAsJsonArray("results");

                for (JsonElement resultElement : resultsArray) {
                    JsonObject movieObject = resultElement.getAsJsonObject();

                    Long id = movieObject.get("id").getAsLong();
                    String title = movieObject.get("title").getAsString();
                    String overview = movieObject.get("overview").getAsString();
                    String posterPath = movieObject.get("poster_path").getAsString();
                    String releaseDate = movieObject.get("release_date").getAsString();

                    // 장르 ID 리스트를 String으로 변환
                    JsonArray genreIdsArray = movieObject.getAsJsonArray("genre_ids");
                    List<Integer> genreIdsList = new ArrayList<>();
                    for (JsonElement genreElement : genreIdsArray) {
                        genreIdsList.add(genreElement.getAsInt());
                    }
                    String genreIds = genreIdsList.toString();

                    // score 초기화
                    Map<String, Object> score = Map.of(
                            "avgActorSkill", 0.0,
                            "avgDirectorSkill", 0.0,
                            "avgLineSkill", 0.0,
                            "avgMusicSkill", 0.0,
                            "avgSceneSkill", 0.0,
                            "avgStorySkill", 0.0,
                            "totalAverageSkill", 0.0
                    );
                    // myScore 초기화
                    Map<String, Object> myScore = null;
                    try {
                        score = reviewService.getAverageSkillsByMovieId(id);
                        myScore = reviewService.getLatestReviewSkills(memberId, id);
                    } catch (Exception e) {
                        log.warn("Review data not found for movie ID: {}", id, e);
                        score = Map.of("avgActorSkill", 0.0, "avgDirectorSkill", 0.0, "avgLineSkill", 0.0, "avgMusicSkill", 0.0, "avgSceneSkill", 0.0,  "avgStorySkill", 0.0, "totalAverageSkill", 0.0);
                        myScore = null;
//                        myScore = Map.of("actorSkill", 0, "directorSkill", 0, "lineSkill", 0, "musicSkill", 0, "sceneSkill", 0,  "storySkill", 0, "avgSkill", 0);
                    }

                    MovieCardDto movieCardDto = new MovieCardDto(id, title, overview, posterPath, releaseDate, genreIds, score, myScore);
                    allMovies.add(movieCardDto);
                }
            }
        }
        return allMovies;
    }

    @Override
    public List<MovieCardDto> getPopularMovies(Long memberId) throws Exception {
        List<MovieCardDto> allMovies = new ArrayList<>();
        String PopularUrl = "popular?language=ko-KR&page=";

        for(int page = 1; page <=2; page++){
            Request request = new Request.Builder()
                    .url(TMDB_API_URL + PopularUrl + page + "&region=KR")
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", AUTH_TOKEN )
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new Exception("Unexpected code " + response);
                }

                String jsonResponse = response.body().string();
                log.info("MovieServiceImpl: " + jsonResponse);

                JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
                JsonArray resultsArray = jsonObject.getAsJsonArray("results");

                for (JsonElement resultElement : resultsArray) {
                    JsonObject movieObject = resultElement.getAsJsonObject();

                    Long id = movieObject.get("id").getAsLong();
                    String title = movieObject.get("title").getAsString();
                    String overview = movieObject.get("overview").getAsString();
                    String posterPath = movieObject.get("poster_path").getAsString();
                    String releaseDate = movieObject.get("release_date").getAsString();

                    // 장르 ID 리스트를 String으로 변환
                    JsonArray genreIdsArray = movieObject.getAsJsonArray("genre_ids");
                    List<Integer> genreIdsList = new ArrayList<>();
                    for (JsonElement genreElement : genreIdsArray) {
                        genreIdsList.add(genreElement.getAsInt());
                    }
                    String genreIds = genreIdsList.toString();

                    // score 초기화
                    Map<String, Object> score = Map.of(
                            "avgActorSkill", 0.0,
                            "avgDirectorSkill", 0.0,
                            "avgLineSkill", 0.0,
                            "avgMusicSkill", 0.0,
                            "avgSceneSkill", 0.0,
                            "avgStorySkill", 0.0,
                            "totalAverageSkill", 0.0
                    );
                    // myScore 초기화
                    Map<String, Object> myScore = null;
                    try {
                        score = reviewService.getAverageSkillsByMovieId(id);
                        myScore = reviewService.getLatestReviewSkills(memberId, id);
                    } catch (Exception e) {
                        log.warn("Review data not found for movie ID: {}", id, e);
                        score = Map.of("avgActorSkill", 0.0, "avgDirectorSkill", 0.0, "avgLineSkill", 0.0, "avgMusicSkill", 0.0, "avgSceneSkill", 0.0,  "avgStorySkill", 0.0, "totalAverageSkill", 0.0);
                        myScore = null;
//                        myScore = Map.of("actorSkill", 0, "directorSkill", 0, "lineSkill", 0, "musicSkill", 0, "sceneSkill", 0,  "storySkill", 0, "avgSkill", 0.0);
                    }

                    MovieCardDto movieCardDto = new MovieCardDto(id, title, overview, posterPath, releaseDate, genreIds, score, myScore);
                    allMovies.add(movieCardDto);
                }
            }
        }
        return allMovies;
    }

//    @Override
//    public List<MovieCardDto> getRecommendMovies(Long movieId, Long memberId) throws Exception {
//        List<MovieCardDto> allMovies = new ArrayList<>();
//        String RecommendUrl = movieId + "/recommendations?language=ko-KR&page=1";
//
//        Request request = new Request.Builder()
//                .url(TMDB_API_URL + RecommendUrl + "&region=KR")
//                .get()
//                .addHeader("accept", "application/json")
//                .addHeader("Authorization", AUTH_TOKEN)
//                .build();
//
//        try (Response response = client.newCall(request).execute()) {
//            if (!response.isSuccessful()) {
//                throw new Exception("Unexpected code " + response);
//            }
//
//            String jsonResponse = response.body().string();
//            log.info("MovieServiceImpl: " + jsonResponse);
//
//            JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
//            JsonArray resultsArray = jsonObject.getAsJsonArray("results");
//
//            for (JsonElement resultElement : resultsArray) {
//                JsonObject movieObject = resultElement.getAsJsonObject();
//
//                Long id = movieObject.get("id").getAsLong();
//                String title = movieObject.get("title").getAsString();
//                String overview = movieObject.get("overview").getAsString();
//                String posterPath = movieObject.get("poster_path").getAsString();
//                String releaseDate = movieObject.get("release_date").getAsString();
//
//                // 장르 ID list -> String
//                JsonArray genreIdsArray = movieObject.getAsJsonArray("genre_ids");
//                List<Integer> genreIdsList = new ArrayList<>();
//                for (JsonElement genreElement : genreIdsArray) {
//                    genreIdsList.add(genreElement.getAsInt());
//                }
//                String genreIds = genreIdsList.toString();
//
//                // score 초기화
//                Map<String, Object> score = Map.of(
//                        "avgActorSkill", 0.0,
//                        "avgDirectorSkill", 0.0,
//                        "avgLineSkill", 0.0,
//                        "avgMusicSkill", 0.0,
//                        "avgSceneSkill", 0.0,
//                        "avgStorySkill", 0.0,
//                        "totalAverageSkill", 0.0
//                );
//                // myScore 초기화
//                Map<String, Object> myScore = null;
//                try {
//                    score = reviewService.getAverageSkillsByMovieId(id);
//                    myScore = reviewService.getLatestReviewSkills(memberId, id);
//                } catch (Exception e) {
//                    log.warn("Review data not found for movie ID: {}", id, e);
//                    score = Map.of("avgActorSkill", 0.0, "avgDirectorSkill", 0.0, "avgLineSkill", 0.0, "avgMusicSkill", 0.0, "avgSceneSkill", 0.0, "avgStorySkill", 0.0, "totalAverageSkill", 0.0);
//                    myScore = null;
//                }
//
//                MovieCardDto movieCardDto = new MovieCardDto(id, title, overview, posterPath, releaseDate, genreIds, score, myScore);
//                allMovies.add(movieCardDto);
//            }
//        }
//
//        return allMovies;
//    }
    @Override
    public List<MovieCardDto> getRecommendMovies(Long movieId, Long memberId) throws Exception {
        List<MovieCardDto> allMovies = new ArrayList<>();
        String RecommendUrl = movieId + "/recommendations?language=ko-KR&page=1";

        Request request = new Request.Builder()
                .url(TMDB_API_URL + RecommendUrl + "&region=KR")
                .get()
                .addHeader("accept", "application/json")
                .addHeader("Authorization", AUTH_TOKEN)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("Unexpected code " + response);
            }

            String jsonResponse = response.body().string();
            log.info("MovieServiceImpl: " + jsonResponse);

            JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonArray resultsArray = jsonObject.getAsJsonArray("results");

            for (JsonElement resultElement : resultsArray) {
                JsonObject movieObject = resultElement.getAsJsonObject();

                // 영화 ID 가져오기
                Long id = movieObject.get("id").getAsLong();

                // DB에 없는 ID는 건너뛰기
                if (movieRepository.findById(id).isEmpty()) {
                    continue;
                }

                // 영화 정보 파싱
                String title = movieObject.get("title").getAsString();
                String overview = movieObject.get("overview").getAsString();
                String posterPath = movieObject.get("poster_path").getAsString();
                String releaseDate = movieObject.get("release_date").getAsString();

                // 장르 ID list -> String
                JsonArray genreIdsArray = movieObject.getAsJsonArray("genre_ids");
                List<Integer> genreIdsList = new ArrayList<>();
                for (JsonElement genreElement : genreIdsArray) {
                    genreIdsList.add(genreElement.getAsInt());
                }
                String genreIds = genreIdsList.toString();

                // score 초기화
                Map<String, Object> score = Map.of(
                        "avgActorSkill", 0.0,
                        "avgDirectorSkill", 0.0,
                        "avgLineSkill", 0.0,
                        "avgMusicSkill", 0.0,
                        "avgSceneSkill", 0.0,
                        "avgStorySkill", 0.0,
                        "totalAverageSkill", 0.0
                );
                // myScore 초기화
                Map<String, Object> myScore = null;
                try {
                    score = reviewService.getAverageSkillsByMovieId(id);
                    myScore = reviewService.getLatestReviewSkills(memberId, id);
                } catch (Exception e) {
                    log.warn("Review data not found for movie ID: {}", id, e);
                    score = Map.of(
                            "avgActorSkill", 0.0,
                            "avgDirectorSkill", 0.0,
                            "avgLineSkill", 0.0,
                            "avgMusicSkill", 0.0,
                            "avgSceneSkill", 0.0,
                            "avgStorySkill", 0.0,
                            "totalAverageSkill", 0.0
                    );
                    myScore = null;
                }

                // MovieCardDto 생성 및 리스트에 추가
                MovieCardDto movieCardDto = new MovieCardDto(id, title, overview, posterPath, releaseDate, genreIds, score, myScore);
                allMovies.add(movieCardDto);
            }
        }

        return allMovies;
    }


//    @Override
//    public MovieDetailsDto getMovieDetails(Long id) throws Exception {
//        log.info("MovieServiceImpl: 지금 영화 데이터 TMDB에서 가져오는 중");
//        String MovieDetailUrl = TMDB_API_URL + id + "?append_to_response=credits%2Cvideos%2Crecommendations&language=ko-KR";//detail & videos & recommendations
//
//        Request request = new Request.Builder()
//                .url(MovieDetailUrl)
//                .get()
//                .addHeader("accept", "application/json")
//                .addHeader("Authorization", AUTH_TOKEN)
//                .build();
//
//        try (Response response = client.newCall(request).execute()) {
//            if (response.isSuccessful() && response.body() != null) {
//                String jsonResponse = response.body().string();
//
//                // 영화 상세정보 뽑아내기
//                JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
//                String title = jsonObject.get("title").getAsString();
//                int runtime = jsonObject.get("runtime").getAsInt();
//                String overview = jsonObject.get("overview").getAsString();
//                String releaseDate = jsonObject.get("release_date").getAsString();
//                String posterPath = jsonObject.get("poster_path").getAsString();
//                String backdropPath = jsonObject.get("backdrop_path").getAsString();
//                //Double totalAverageSkill = jsonObject.get("totalAverageSkill").getAsDouble();
//
//                // 이미지 리스트 설정
//                List<MovieDetailsDto.Images> imagesList = new ArrayList<>();
//                MovieDetailsDto.Images images = new MovieDetailsDto.Images();
//                images.setPoster_path(posterPath);
//                images.setBackdrop_path(backdropPath);
//                imagesList.add(images);
//
//                // 장르 리스트 설정
//                List<MovieDetailsDto.Genres> genres = new ArrayList<>();
//                jsonObject.getAsJsonArray("genres").forEach(genreElement -> {
//                    JsonObject genreObject = genreElement.getAsJsonObject();
//                    MovieDetailsDto.Genres genre = new MovieDetailsDto.Genres();
//                    genre.setId(genreObject.get("id").getAsInt());
//                    genre.setName(genreObject.get("name").getAsString());
//                    genres.add(genre);
//                });
//
//                // 비디오 리스트 설정
//                List<MovieDetailsDto.Videos> videosList = new ArrayList<>();
//                jsonObject.getAsJsonObject("videos").getAsJsonArray("results").forEach(videoElement -> {
//                    JsonObject videoObject = videoElement.getAsJsonObject();
//                    MovieDetailsDto.Videos video = new MovieDetailsDto.Videos();
//                    video.setKey(videoObject.get("key").getAsString());
//                    video.setType(videoObject.get("type").getAsString());
//                    videosList.add(video);
//                });
//
//                // credit 리스트 설정 배우 상위 10명
//                List<MovieDetailsDto.Credits> credits = new ArrayList<>();
//                JsonArray castArray = jsonObject.getAsJsonObject("credits").getAsJsonArray("cast");
//                JsonArray crewArray = jsonObject.getAsJsonObject("credits").getAsJsonArray("crew");
//
//                // 배우 기본 10명(10명 안되면 전체 배우 가져옴)
//                int castCount = Math.min(10, castArray.size());
//                for (int i = 0; i < castArray.size(); i++) {
//                    if (i >= castCount) break;
//
//                    JsonObject creditObject = castArray.get(i).getAsJsonObject();
//                    MovieDetailsDto.Credits credit = new MovieDetailsDto.Credits();
//
//                    credit.setType(creditObject.has("known_for_department") && !creditObject.get("known_for_department").isJsonNull()
//                            ? creditObject.get("known_for_department").getAsString()
//                            : "Unknown");
//
//                    credit.setName(creditObject.has("name") && !creditObject.get("name").isJsonNull()
//                            ? creditObject.get("name").getAsString()
//                            : "Unknown");
//
//                    credit.setProfile(creditObject.has("profile_path") && !creditObject.get("profile_path").isJsonNull()
//                            ? creditObject.get("profile_path").getAsString()
//                            : null);
//
//                    credits.add(credit);
//                }
//
//                // 감독 1명 배우 10명
//                for (int i = 0; i < crewArray.size(); i++) {
//                    JsonObject crewObject = crewArray.get(i).getAsJsonObject();
//
//                    if (crewObject.has("job") && !crewObject.get("job").isJsonNull() && "Director".equals(crewObject.get("job").getAsString())) {
//                        MovieDetailsDto.Credits director = new MovieDetailsDto.Credits();
//
//                        director.setType("Director");
//                        director.setName(crewObject.has("name") && !crewObject.get("name").isJsonNull()
//                                ? crewObject.get("name").getAsString()
//                                : "Unknown");
//
//                        director.setProfile(crewObject.has("profile_path") && !crewObject.get("profile_path").isJsonNull()
//                                ? crewObject.get("profile_path").getAsString()
//                                : null);
//
//                        credits.add(director); // 감독 정보를 배우 리스트에 추가
//                        break;
//                    }
//                }
//
//                // recommendations 리스트
//                List<MovieDetailsDto.Recommends> recommends = new ArrayList<>();
//                jsonObject.getAsJsonObject("recommendations").getAsJsonArray("results").forEach(recommendsElement -> {
//                    JsonObject recommendsObject = recommendsElement.getAsJsonObject();
//                    MovieDetailsDto.Recommends recommend = new MovieDetailsDto.Recommends();
//                    recommend.setId(recommendsObject.get("id").getAsLong());
//                    recommends.add(recommend);
//                });
//
//
//                // JSON 문자열로 변환
//                String imagesJson = gson.toJson(imagesList);
//                String videosJson = gson.toJson(videosList);
//                String genresJson = gson.toJson(genres);
//
//
//                MovieDetailsDto movieDetailsDto = new MovieDetailsDto(id, title, overview, releaseDate, runtime, imagesJson, videosJson, genresJson, credits, recommends);
//                return movieDetailsDto; //화면에 보여주기
//            } else {
//                throw new IOException("Unexpected response code: " + response.code());
//            }
//        }
//    }

    @Override
    public MovieDetailsDto getMovieDetails(Long id) throws Exception {
        log.info("MovieServiceImpl: 지금 영화 데이터 TMDB에서 가져오는 중");
        String MovieDetailUrl = TMDB_API_URL + id + "?append_to_response=credits%2Cvideos%2Crecommendations&language=ko-KR"; // detail & videos & recommendations

        Request request = new Request.Builder()
                .url(MovieDetailUrl)
                .get()
                .addHeader("accept", "application/json")
                .addHeader("Authorization", AUTH_TOKEN)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String jsonResponse = response.body().string();

                // 영화 상세정보 뽑아내기
                JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();

                String title = jsonObject.has("title") && !jsonObject.get("title").isJsonNull() ? jsonObject.get("title").getAsString() : "Unknown";
                int runtime = jsonObject.has("runtime") && !jsonObject.get("runtime").isJsonNull() ? jsonObject.get("runtime").getAsInt() : 0;
                String overview = jsonObject.has("overview") && !jsonObject.get("overview").isJsonNull() ? jsonObject.get("overview").getAsString() : "No overview available";
                String releaseDate = jsonObject.has("release_date") && !jsonObject.get("release_date").isJsonNull() ? jsonObject.get("release_date").getAsString() : "Unknown";
                String posterPath = jsonObject.has("poster_path") && !jsonObject.get("poster_path").isJsonNull() ? jsonObject.get("poster_path").getAsString() : null;
                String backdropPath = jsonObject.has("backdrop_path") && !jsonObject.get("backdrop_path").isJsonNull() ? jsonObject.get("backdrop_path").getAsString() : null;

                // 이미지 리스트 설정
                List<MovieDetailsDto.Images> imagesList = new ArrayList<>();
                MovieDetailsDto.Images images = new MovieDetailsDto.Images();
                images.setPoster_path(posterPath);
                images.setBackdrop_path(backdropPath);
                imagesList.add(images);

                // 장르 리스트 설정
                List<MovieDetailsDto.Genres> genres = new ArrayList<>();
                if (jsonObject.has("genres") && !jsonObject.get("genres").isJsonNull()) {
                    jsonObject.getAsJsonArray("genres").forEach(genreElement -> {
                        JsonObject genreObject = genreElement.getAsJsonObject();
                        MovieDetailsDto.Genres genre = new MovieDetailsDto.Genres();
                        genre.setId(genreObject.has("id") && !genreObject.get("id").isJsonNull() ? genreObject.get("id").getAsInt() : 0);
                        genre.setName(genreObject.has("name") && !genreObject.get("name").isJsonNull() ? genreObject.get("name").getAsString() : "Unknown");
                        genres.add(genre);
                    });
                }

                // 비디오 리스트 설정
                List<MovieDetailsDto.Videos> videosList = new ArrayList<>();
                if (jsonObject.has("videos") && !jsonObject.get("videos").isJsonNull()) {
                    jsonObject.getAsJsonObject("videos").getAsJsonArray("results").forEach(videoElement -> {
                        JsonObject videoObject = videoElement.getAsJsonObject();
                        MovieDetailsDto.Videos video = new MovieDetailsDto.Videos();
                        video.setKey(videoObject.has("key") && !videoObject.get("key").isJsonNull() ? videoObject.get("key").getAsString() : "Unknown");
                        video.setType(videoObject.has("type") && !videoObject.get("type").isJsonNull() ? videoObject.get("type").getAsString() : "Unknown");
                        videosList.add(video);
                    });
                }

                // credit 리스트 설정 배우 상위 10명
                List<MovieDetailsDto.Credits> credits = new ArrayList<>();
                JsonArray castArray = jsonObject.has("credits") && !jsonObject.get("credits").isJsonNull() ? jsonObject.getAsJsonObject("credits").getAsJsonArray("cast") : new JsonArray();
                JsonArray crewArray = jsonObject.has("credits") && !jsonObject.get("credits").isJsonNull() ? jsonObject.getAsJsonObject("credits").getAsJsonArray("crew") : new JsonArray();

                // 배우 기본 10명(10명 안되면 전체 배우 가져옴)
                int castCount = Math.min(10, castArray.size());
                for (int i = 0; i < castArray.size(); i++) {
                    if (i >= castCount) break;

                    JsonObject creditObject = castArray.get(i).getAsJsonObject();
                    MovieDetailsDto.Credits credit = new MovieDetailsDto.Credits();

                    credit.setType(creditObject.has("known_for_department") && !creditObject.get("known_for_department").isJsonNull()
                            ? creditObject.get("known_for_department").getAsString()
                            : "Unknown");

                    credit.setName(creditObject.has("name") && !creditObject.get("name").isJsonNull()
                            ? creditObject.get("name").getAsString()
                            : "Unknown");

                    credit.setProfile(creditObject.has("profile_path") && !creditObject.get("profile_path").isJsonNull()
                            ? creditObject.get("profile_path").getAsString()
                            : null);

                    credits.add(credit);
                }

                // 감독 1명 배우 10명
                for (int i = 0; i < crewArray.size(); i++) {
                    JsonObject crewObject = crewArray.get(i).getAsJsonObject();

                    if (crewObject.has("job") && !crewObject.get("job").isJsonNull() && "Director".equals(crewObject.get("job").getAsString())) {
                        MovieDetailsDto.Credits director = new MovieDetailsDto.Credits();

                        director.setType("Director");
                        director.setName(crewObject.has("name") && !crewObject.get("name").isJsonNull()
                                ? crewObject.get("name").getAsString()
                                : "Unknown");

                        director.setProfile(crewObject.has("profile_path") && !crewObject.get("profile_path").isJsonNull()
                                ? crewObject.get("profile_path").getAsString()
                                : null);

                        credits.add(director); // 감독 정보를 배우 리스트에 추가
                        break;
                    }
                }

                // recommendations 리스트
                List<MovieDetailsDto.Recommends> recommends = new ArrayList<>();
                if (jsonObject.has("recommendations") && !jsonObject.get("recommendations").isJsonNull()) {
                    jsonObject.getAsJsonObject("recommendations").getAsJsonArray("results").forEach(recommendsElement -> {
                        JsonObject recommendsObject = recommendsElement.getAsJsonObject();
                        MovieDetailsDto.Recommends recommend = new MovieDetailsDto.Recommends();
                        recommend.setId(recommendsObject.has("id") && !recommendsObject.get("id").isJsonNull() ? recommendsObject.get("id").getAsLong() : 0);
                        recommends.add(recommend);
                    });
                }

                // JSON 문자열로 변환
                String imagesJson = gson.toJson(imagesList);
                String videosJson = gson.toJson(videosList);
                String genresJson = gson.toJson(genres);

                MovieDetailsDto movieDetailsDto = new MovieDetailsDto(id, title, overview, releaseDate, runtime, imagesJson, videosJson, genresJson, credits, recommends);
                return movieDetailsDto; // 화면에 보여주기
            } else {
                throw new IOException("Unexpected response code: " + response.code());
            }
        }
    }

    @Override
    public List<Long> SaveTopRatedId() throws Exception {
        List<Long> TopRatedMoviesId = new ArrayList<>();
        String TopRatedUrl = "top_rated?language=ko-KR&page=";
        String PopularUrl = "popular?language=ko-KR&page=";
        String NowPlayingUrl = "upcoming?language=ko-KR&page=";

        for (int page = 1; page <= 500; page++) {
            Request request = new Request.Builder()
                    .url(TMDB_API_URL + TopRatedUrl + page + "&region=KR")
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", AUTH_TOKEN)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new Exception("Unexpected code " + response);
                }

                String jsonResponse = response.body().string();
                JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();

                // results 배열에서 영화 ID 뽑기
                jsonObject.getAsJsonArray("results").forEach(movieElement -> {
                    JsonObject movieObject = movieElement.getAsJsonObject();
                    Long movieId = movieObject.get("id").getAsLong();

                    if(!movieRepository.existsById(movieId)){
                        TopRatedMoviesId.add(movieId);
                    }

                    // db에 저장
                    TopRatedMovieIdEntity topRatedMovieIdEntity = new TopRatedMovieIdEntity(movieId);
                    topRatedMovieIdRepository.save(topRatedMovieIdEntity);
                });
            }
        }

        return TopRatedMoviesId;
    }

    @Override
    public List<Long> SavePopularId() throws Exception {
        List<Long> PopularMoviesId = new ArrayList<>();
        String PopularUrl = "popular?language=ko-KR&page=";

        for (int page = 1; page <= 2; page++) {
            Request request = new Request.Builder()
                    .url(TMDB_API_URL + PopularUrl + page + "&region=KR")
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", AUTH_TOKEN)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new Exception("Unexpected code " + response);
                }

                String jsonResponse = response.body().string();
                JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();

                // results 배열에서 영화 ID 뽑기
                jsonObject.getAsJsonArray("results").forEach(movieElement -> {
                    JsonObject movieObject = movieElement.getAsJsonObject();
                    Long movieId = movieObject.get("id").getAsLong();

//                    if(!movieRepository.existsById(movieId)){
//                        PopularMoviesId.add(movieId);
//                    }
                    PopularMoviesId.add(movieId);

                    // db에 저장
                    TopRatedMovieIdEntity topRatedMovieIdEntity = new TopRatedMovieIdEntity(movieId);
                    topRatedMovieIdRepository.save(topRatedMovieIdEntity);

                    MovieDetailsDto movieDetails = null;
                    try {
                        movieDetails = getMovieDetails(movieId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    System.out.println("hhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhh");
                    movieRepository.save(toEntity(movieDetails)); // DB에 저장
                });
            }
        }
        return PopularMoviesId;
    }

    @Override
    public List<Long> SaveNowPlayingId() throws Exception {
        List<Long> NowPlayingId = new ArrayList<>();
        String NowPlayingUrl = "now_playing?language=ko-KR&page=";

        for (int page = 1; page <= 2; page++) {
            Request request = new Request.Builder()
                    .url(TMDB_API_URL + NowPlayingUrl + page + "&region=KR")
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", AUTH_TOKEN)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new Exception("Unexpected code " + response);
                }

                String jsonResponse = response.body().string();
                JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();

                // results 배열에서 영화 ID 뽑기
                jsonObject.getAsJsonArray("results").forEach(movieElement -> {
                    JsonObject movieObject = movieElement.getAsJsonObject();
                    Long movieId = movieObject.get("id").getAsLong();

//                    if(!movieRepository.existsById(movieId)){
//                        NowPlayingId.add(movieId);
//                    }

                    NowPlayingId.add(movieId);

                    // db에 저장
                    TopRatedMovieIdEntity topRatedMovieIdEntity = new TopRatedMovieIdEntity(movieId);
                    topRatedMovieIdRepository.save(topRatedMovieIdEntity);

                    MovieDetailsDto movieDetails = null;
                    try {
                        movieDetails = getMovieDetails(movieId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    System.out.println("hhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhh");
                    movieRepository.save(toEntity(movieDetails)); // DB에 저장
                });
            }
        }

        return NowPlayingId;
    }

    @Override
    public List<Long> SaveUpComingId() throws Exception {
        List<Long> TopRatedMoviesId = new ArrayList<>();
        String NowPlayingUrl = "now_playing?language=ko-KR&page=";

        for (int page = 1; page <= 2; page++) {
            Request request = new Request.Builder()
                    .url(TMDB_API_URL + NowPlayingUrl + page + "&region=KR")
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", AUTH_TOKEN)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new Exception("Unexpected code " + response);
                }

                String jsonResponse = response.body().string();
                JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();

                // results 배열에서 영화 ID 뽑기
                jsonObject.getAsJsonArray("results").forEach(movieElement -> {
                    JsonObject movieObject = movieElement.getAsJsonObject();
                    Long movieId = movieObject.get("id").getAsLong();

                    if(!movieRepository.existsById(movieId)){
                        TopRatedMoviesId.add(movieId);
                    }

                    // db에 저장
                    TopRatedMovieIdEntity topRatedMovieIdEntity = new TopRatedMovieIdEntity(movieId);
                    topRatedMovieIdRepository.save(topRatedMovieIdEntity);
                });
            }
        }

        return TopRatedMoviesId;
    }

    public List<MovieDetailsDto> getTopRatedMovieDetails() throws Exception {
        List<MovieDetailsDto> movieDetailsList = new ArrayList<>();

        // DB에서 저장된 모든 영화 ID 가져오기
        List<Long> movieIds = topRatedMovieIdRepository.findAll()
                .stream()
                .map(TopRatedMovieIdEntity::getId)
                .collect(Collectors.toList());

        // 이미 저장된 영화 ID 가져오기
        Set<Long> existingMovieIds = movieRepository.findAll()
                .stream()
                .map(MovieDetailEntity::getId) // MovieEntity의 ID 필드 기준
                .collect(Collectors.toSet());

        // 각 ID에 대해 영화 상세 정보 요청
        for (Long id : movieIds) {
            // 이미 저장된 ID는 건너뛰기
            if (existingMovieIds.contains(id)) {
                continue;
            }

            try {
                MovieDetailsDto movieDetails = getMovieDetails(id);
                movieRepository.save(toEntity(movieDetails)); // DB에 저장

                // recommendations 저장
                for (MovieDetailsDto.Recommends recommId : movieDetails.getRecommendations()) {
                    movieRecommendService.saveRecommendations(
                            MovieRecommendDto.builder()
                                    .movieId(id)
                                    .recommendationMovieId(recommId.getId())
                                    .build()
                    );
                }

                // credits 저장
                for (MovieDetailsDto.Credits creditId : movieDetails.getCredits()) {
                    movieCreditService.saveMovieCredit(
                            MovieCreditsDto.builder()
                                    .movieId(id)
                                    .name(creditId.getName())
                                    .type(creditId.getType())
                                    .profile(creditId.getProfile())
                                    .build()
                    );
                }

                movieDetailsList.add(movieDetails);
            } catch (Exception e) {
                // 예외 발생 시 로그 출력 및 작업 중단 방지
                log.error("Error processing movie ID " + id, e);
            }
        }

        return movieDetailsList;
    }


//    @Override
//    public List<MovieDetailsDto> getTopRatedMovieDetails() throws Exception {
//        List<MovieDetailsDto> movieDetailsList = new ArrayList<>();
//
//        // DB에서 저장된 모든 영화 ID 가져오기
//        List<Long> movieIds = topRatedMovieIdRepository.findAll()
//                .stream()
//                .map(TopRatedMovieIdEntity::getId)
//                .collect(Collectors.toList());
//
//        // 각 ID에 대해 영화 상세 정보 요청
//        for (Long id : movieIds) {
//            MovieDetailsDto movieDetails = getMovieDetails(id);
//            movieRepository.save(toEntity(movieDetails)); //db에 저장
//
//            // recommendations 저장
//            for (MovieDetailsDto.Recommends recommId : movieDetails.getRecommendations()) {
//                MovieRecommendDto recommendDto = MovieRecommendDto.builder()
//                        .movieId(id)
//                        .recommendationMovieId(recommId.getId())
//                        .build();
//                movieRecommendService.saveRecommendations(recommendDto);
//            }
//
//            // credits 저장
//            for (MovieDetailsDto.Credits creditId : movieDetails.getCredits()) {
//                MovieCreditsDto movieCreditsDto = MovieCreditsDto.builder()
//                        .movieId(id)
//                        .name(creditId.getName())
//                        .type(creditId.getType())
//                        .profile(creditId.getProfile())
//                        .build();
//                movieCreditService.saveMovieCredit(movieCreditsDto);
//            }
//
//            movieDetailsList.add(movieDetails);
//        }
//        return movieDetailsList;
//    }

    @Override
    @Transactional
    public MovieDetailsDto getTopRatedMovieDetailsInDB(Long movieId, Long memberId) throws Exception{
        log.info("MovieServiceImpl: 지금 영화 데이터 DB에서 id로 검색");
        Optional<MovieDetailEntity> movieDetail = movieRepository.findById(movieId);
        MovieDetailEntity movieDetailEntity = movieDetail.orElseThrow();

        // 영화 상세 정보를 DTO로 변환
        MovieDetailsDto movieDetailsDto = toDto(movieDetailEntity);

        // score 초기화
        Map<String, Object> score = Map.of(
                "avgActorSkill", 0.0,
                "avgDirectorSkill", 0.0,
                "avgLineSkill", 0.0,
                "avgMusicSkill", 0.0,
                "avgSceneSkill", 0.0,
                "avgStorySkill", 0.0,
                "totalAverageSkill", 0.0
        );

        // myScore 초기화
        Map<String, Object> myScore = null;

        try {
            score = reviewService.getAverageSkillsByMovieId(movieId);
            myScore = reviewService.getLatestReviewSkills(memberId, movieId);
        } catch (Exception e) {
            log.warn("Review data not found for movie ID: {}", movieId, e);
            score = Map.of("avgActorSkill", 0.0, "avgDirectorSkill", 0.0, "avgLineSkill", 0.0, "avgMusicSkill", 0.0, "avgSceneSkill", 0.0,  "avgStorySkill", 0.0, "totalAverageSkill", 0.0);
            myScore = null;
        }
        movieDetailsDto.setScore(score);
        movieDetailsDto.setMyScore(myScore);

        // 어워즈 관련 데이터 DTO 생성
//        AwardsAllScoreDto awardsAllScoreDto;

        List<AwardsEntity> nominatedList = awardsRepository.findByStatus(1); //어차피 하나임

        // movieId와 일치하는 어워즈를 찾아서 해당 어워즈 정보를 처리
        for (AwardsEntity awardsEntity : nominatedList) {
            // nominated1, nominated2, nominated3, nominated4 중 하나라도 movieId와 일치하는지 확인
            if (awardsEntity.getNominated1().equals(movieId) ||
                    awardsEntity.getNominated2().equals(movieId) ||
                    awardsEntity.getNominated3().equals(movieId) ||
                    awardsEntity.getNominated4().equals(movieId)) {

                // 어워즈 관련 데이터 DTO 설정
                LocalDateTime awardStartDate = awardsEntity.getStartDateTime();
                LocalDateTime awardEndDate = awardsEntity.getEndDateTime();

                // awardsScore 초기화
                Map<String, Object> awardsScore = Map.of(
                        "avgActorSkillWithAwards", 0.0,
                        "avgDirectorSkillWithAwards", 0.0,
                        "avgLineSkillWithAwards", 0.0,
                        "avgMusicSkillWithAwards", 0.0,
                        "avgSceneSkillWithAwards", 0.0,
                        "avgStorySkillWithAwards", 0.0,
                        "totalAverageSkillWithAwards", 0.0
                );

                // awardsMyScore 초기화
                Map<String, Object> awardsMyScore = null;

                try {
                    // 어워즈 기간에 대한 점수 데이터 가져오기
                    awardsScore = reviewService.getAverageSkillsByMovieIdAndDateRange(movieId, awardStartDate, awardEndDate);
                    awardsMyScore = reviewService.getAwardsReviewSkills(memberId, movieId, awardStartDate, awardEndDate);

                    // awardsMyScore가 비어 있는 경우 null로 처리
                    if (awardsMyScore == null || awardsMyScore.isEmpty()) {
                        awardsMyScore = null;
                    }
                } catch (Exception e) {
                    log.warn("Awards review data not found for movie ID: {} and member ID: {}", movieId, memberId, e);
                    awardsScore = Map.of(
                            "avgActorSkillWithAwards", 0.0,
                            "avgDirectorSkillWithAwards", 0.0,
                            "avgLineSkillWithAwards", 0.0,
                            "avgMusicSkillWithAwards", 0.0,
                            "avgSceneSkillWithAwards", 0.0,
                            "avgStorySkillWithAwards", 0.0,
                            "totalAverageSkillWithAwards", 0.0
                    );
                    awardsMyScore = null; // 데이터가 없으므로 null로 설정
                }

// 어워즈 관련 score 두 개 값 넣어주기
                AwardsAllScoreDto awardsAllScoreDto = new AwardsAllScoreDto(movieId, awardsScore, awardsMyScore);


                // 결과를 movieDetailsDto에 설정
                movieDetailsDto.setAwardsAllScoreDto(awardsAllScoreDto);
                break;  // 일치하는 어워즈를 찾은 후에는 더 이상 반복할 필요 없음
            }
        }

        // awardsNames 초기화
        List<String> awardsNames = List.of();
        List<AwardsEntity> awardsEntityList = awardsRepository.findByTopMovieId(movieId);

        if (awardsEntityList != null && !awardsEntityList.isEmpty()) {
            // AwardsEntity에서 awardsName만 추출
            awardsNames = awardsEntityList.stream()
                    .map(AwardsEntity::getAwardName)
                    .toList();
            movieDetailsDto.setAwardsNames(awardsNames);
        } else {
            movieDetailsDto.setAwardsNames(List.of()); // 빈 리스트로 초기화
        }
        return movieDetailsDto;
    }

    @Override
    @Transactional
    public MovieDetailsDto getTopRatedMovieDetailsInDBForAwards(Long movieId) throws Exception{
        log.info("MovieServiceImpl: 지금 영화 데이터 DB에서 id로 검색");
        Optional<MovieDetailEntity> movieDetail = movieRepository.findById(movieId);
        MovieDetailEntity movieDetailEntity = movieDetail.orElseThrow();

        // 영화 상세 정보를 DTO로 변환
        MovieDetailsDto movieDetailsDto = toDto(movieDetailEntity);

        // 기본 점수 값 설정
        Map<String, Object> score = Map.of(
                "avgActorSkill", 0.0, "avgDirectorSkill", 0.0, "avgLineSkill", 0.0,
                "avgMusicSkill", 0.0, "avgSceneSkill", 0.0, "avgStorySkill", 0.0,
                "totalAverageSkill", 0.0
        );

        try {
            score = reviewService.getAverageSkillsByMovieId(movieId);
        } catch (Exception e) {
            log.warn("Review data not found for movie ID: {}", movieId, e);
            score = Map.of(
                    "avgActorSkill", 0.0, "avgDirectorSkill", 0.0, "avgLineSkill", 0.0,
                    "avgMusicSkill", 0.0, "avgSceneSkill", 0.0, "avgStorySkill", 0.0,
                    "totalAverageSkill", 0.0
            );
        }

        movieDetailsDto.setScore(score);

        return movieDetailsDto;
    }

    @Transactional
    @Override
    public MovieDetailsDto getPosterGenre(Long movieId) throws Exception{
        log.info("MovieServiceImpl: 지금 영화 데이터 DB에서 id로 검색");
        Optional<MovieDetailEntity> movieDetail = movieRepository.findById(movieId);
        MovieDetailEntity movieDetailEntity = movieDetail.orElseThrow();

        // 영화 상세 정보를 DTO로 변환
        MovieDetailsDto movieDetailsDto = toDto(movieDetailEntity);

        List<MovieDetailsDto.Credits> creditDtos = movieDetailEntity.getCredits().stream()
                .map(credit -> new MovieDetailsDto.Credits(credit.getType(), credit.getName(), credit.getProfile()))
                .collect(Collectors.toList());

        List<MovieDetailsDto.Recommends> recommendDtos = movieDetailEntity.getRecommendations().stream()
                .map(recommend -> new MovieDetailsDto.Recommends(recommend.getRecommendationId()))
                .collect(Collectors.toList());

        MovieDetailsDto.builder()
                .id(movieDetailEntity.getId())
                .title(movieDetailEntity.getTitle())
                .overview(movieDetailEntity.getOverview())
                .release_date(movieDetailEntity.getRelease_date())
                .runtime(movieDetailEntity.getRuntime())
                .images(movieDetailEntity.getImages())
                .videos(movieDetailEntity.getVideos())
                .genres(movieDetailEntity.getGenres())
                .credits(creditDtos)
                .recommendations(recommendDtos)
                .build();

        // 기본 점수 값 설정
        Map<String, Object> score = Map.of(
                "avgActorSkill", 0.0, "avgDirectorSkill", 0.0, "avgLineSkill", 0.0,
                "avgMusicSkill", 0.0, "avgSceneSkill", 0.0, "avgStorySkill", 0.0,
                "totalAverageSkill", 0.0
        );

        try {
            score = reviewService.getAverageSkillsByMovieId(movieId);
        } catch (Exception e) {
            log.warn("Review data not found for movie ID: {}", movieId, e);
        }

        movieDetailsDto.setScore(score);

        return movieDetailsDto;
    }

    @Override
    public MovieDetailsDto searchMovie(String title) throws Exception{
        log.info("MovieServiceImpl: 지금 영화 데이터 DB에서 이름으로 검색");
        Optional<MovieDetailEntity> movieDetail = movieRepository.findByTitle(title);
        MovieDetailEntity movieDetailEntity = movieDetail.orElseThrow();
        return toDto(movieDetailEntity);
    }

    @Override
    public List<MovieCardDto> searchByQuery(String query) {
        List<MovieDetailEntity> movieDetail = movieRepository.findByTitleContaining(query);
        return movieDetail.stream()
                .map(this::toMovieCardDto)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponseDto<MovieCardDto> getAllMovieByKeyword(Long memberId, String title, PageRequestDto pageRequestDto) {
        PageRequest pageable = PageRequest.of(pageRequestDto.getPage() - 1, pageRequestDto.getSize());

        // 제목으로 검색해서 page로 list return
        Page<MovieDetailEntity> searchPage = movieRepository.findByTitleContaining(title, pageable);

        // MovieCardDto 생성 시 score와 myScore 값을 설정
        List<MovieCardDto> movieList = searchPage.getContent().stream()
                .map(entity -> {
                    Map<String, Object> score;
                    Map<String, Object> myScore = null;

                    try {
                        // 각 MovieDetailEntity의 id로 score와 myScore 계산
                        score = reviewService.getAverageSkillsByMovieId(entity.getId());
                        myScore = reviewService.getLatestReviewSkills(memberId, entity.getId());
                    } catch (Exception e) {
                        log.warn("Review data not found for movie ID: {}", entity.getId(), e);
                        score = Map.of(
                                "avgActorSkill", 0.0,
                                "avgDirectorSkill", 0.0,
                                "avgLineSkill", 0.0,
                                "avgMusicSkill", 0.0,
                                "avgSceneSkill", 0.0,
                                "avgStorySkill", 0.0,
                                "totalAverageSkill", 0.0
                        );
                        myScore = null; // myScore 기본값 처리
                    }

                    // DTO 변환 및 score, myScore 설정
                    MovieCardDto dto = toCardDto(entity);
                    dto.setScore(score);
                    dto.setMyScore(myScore);
                    return dto;
                })
                .collect(Collectors.toList());

        // PageResponseDto에 withSearch() 메서드를 사용하여 반환
        return PageResponseDto.<MovieCardDto>withSearch(movieList, pageRequestDto, searchPage.getTotalElements());
    }


//    public List<MovieCardDto> getMovieMemberRecommendations(Long memberId) {
//        // 사용자의 리뷰 목록 가져오기
//        List<ReviewEntity> reviews = reviewRepository.findAllReviewsByMemberId(memberId);
//
//        if (reviews == null || reviews.isEmpty()) {
//            log.warn("사용자 ID {}에 대한 리뷰가 없습니다.", memberId);
//            return new ArrayList<>();
//        }
//
//        // 랜덤으로 리뷰 하나 선택
//        Random random = new Random();
//        int randomIndex = random.nextInt(reviews.size());
//        ReviewEntity randomReview = reviews.get(randomIndex);
//
//        log.info("MovieServiceImpl: 선택된 리뷰 {}", randomReview);
//
//        Long movieId = randomReview.getMovie().getId();
//        log.info("MovieServiceImpl: 선택된 영화 ID {}", movieId);
//
//        try {
//            // 영화 detail 정보 가져오기
//            MovieDetailsDto movieDetailsDto = getTopRatedMovieDetailsInDBForAwards(movieId);
//
//            // 추천 리스트 가져오기
//            List<MovieDetailsDto.Recommends> recommendList = movieDetailsDto.getRecommendations();
//
//            log.info("MovieServiceImpl: 추천 리스트 {}", recommendList);
//
//            if (recommendList == null || recommendList.isEmpty()) {
//                log.warn("MovieServiceImpl: 추천 리스트가 비어 있습니다.");
//                return new ArrayList<>();
//            }
//
//            // 중복 제거를 위해 Set 사용
//            Set<Long> processedIds = new HashSet<>();
//
//            return recommendList.stream()
//                    .map(recommend -> {
//                        Long recommendId = recommend.getId();
//
//                        // 중복된 추천 제거
//                        if (!processedIds.add(recommendId)) {
//                            return null; // 이미 처리된 ID는 제외
//                        }
//
//                        Optional<MovieDetailEntity> movieDetailOptional = movieRepository.findById(recommendId);
//                        if (movieDetailOptional.isEmpty()) {
//                            log.warn("MovieServiceImpl: 추천 영화 ID {}가 DB에 없습니다.", recommendId);
//                            return null;
//                        }
//
//                        MovieDetailEntity movieDetail = movieDetailOptional.get();
//
//                        // 추천 영화의 score와 myScore 계산
//                        Map<String, Object> score = Map.of(
//                                "avgActorSkill", 0.0, "avgDirectorSkill", 0.0, "avgLineSkill", 0.0,
//                                "avgMusicSkill", 0.0, "avgSceneSkill", 0.0, "avgStorySkill", 0.0,
//                                "totalAverageSkill", 0.0
//                        );
//
//                        Map<String, Object> myScore = null;
//
//                        try {
//                            // 추천된 영화의 score 가져오기
//                            score = reviewService.getAverageSkillsByMovieId(recommendId);
//
//                            // 사용자의 최근 리뷰에서 해당 영화의 myScore 가져오기
//                            myScore = reviewService.getLatestReviewSkills(memberId, recommendId);
//                        } catch (Exception e) {
//                            log.warn("Review data not found for movie ID: {}", recommendId, e);
//                            score = Map.of("avgActorSkill", 0.0, "avgDirectorSkill", 0.0, "avgLineSkill", 0.0, "avgMusicSkill", 0.0, "avgSceneSkill", 0.0, "avgStorySkill", 0.0, "totalAverageSkill", 0.0);
//                            myScore = null;
//                        }
//
//                        return MovieCardDto.builder()
//                                .id(movieDetail.getId())
//                                .title(movieDetail.getTitle())
//                                .overview(movieDetail.getOverview())
//                                .poster_path(movieDetail.getImages())
//                                .release_date(movieDetail.getRelease_date())
//                                .genre_ids(movieDetail.getGenres())
//                                .score(score)
//                                .myScore(myScore)
//                                .build();
//                    })
//                    .filter(Objects::nonNull) // Null 값 제거
//                    .collect(Collectors.toList());
//
//        } catch (Exception e) {
//            log.error("MovieServiceImpl: 추천 리스트를 가져오는 중 오류 발생", e);
//            return new ArrayList<>();
//        }
//    }
    @Override
    public Map<String, Object> getMovieMemberRecommendations(Long memberId) {
        Map<String, Object> response = new HashMap<>();

        // 사용자의 리뷰 목록 가져오기
        List<ReviewEntity> reviews = reviewRepository.findAllReviewsByMemberId(memberId);

        if (reviews == null || reviews.isEmpty()) {
            log.warn("사용자 ID {}에 대한 리뷰가 없습니다.", memberId);
            response.put("title", null);
            response.put("recommendations", new ArrayList<>());
            return response;
        }

        // 랜덤으로 리뷰 하나 선택
        Random random = new Random();
        int randomIndex = random.nextInt(reviews.size());
        ReviewEntity randomReview = reviews.get(randomIndex);

        log.info("MovieServiceImpl: 선택된 리뷰 {}", randomReview);

        Long movieId = randomReview.getMovie().getId();
        String title = randomReview.getMovie().getTitle();
        log.info("MovieServiceImpl: 선택된 영화 제목 {}", title);

        response.put("title", title); // 선택된 영화 제목 저장

        try {
            // 영화 detail 정보 가져오기
            MovieDetailsDto movieDetailsDto = getPosterGenre(movieId);

            // 추천 리스트 가져오기
            List<MovieDetailsDto.Recommends> recommendList = movieDetailsDto.getRecommendations();

            log.info("MovieServiceImpl: 추천 리스트 {}", recommendList);

            if (recommendList == null || recommendList.isEmpty()) {
                log.warn("MovieServiceImpl: 추천 리스트가 비어 있습니다.");
                response.put("recommendations", new ArrayList<>());
                return response;
            }

            // 중복 제거를 위해 Set 사용
            Set<Long> processedIds = new HashSet<>();

            List<MovieCardDto> movieCardDtos = recommendList.stream()
                    .map(recommend -> {
                        Long recommendId = recommend.getId();

                        // 중복된 추천 제거
                        if (!processedIds.add(recommendId)) {
                            return null; // 이미 처리된 ID는 제외
                        }

                        Optional<MovieDetailEntity> movieDetailOptional = movieRepository.findById(recommendId);
                        if (movieDetailOptional.isEmpty()) {
                            log.warn("MovieServiceImpl: 추천 영화 ID {}가 DB에 없습니다.", recommendId);
                            return null;
                        }

                        MovieDetailEntity movieDetail = movieDetailOptional.get();

                        int endIndex = movieDetail.getImages().indexOf(",", 17);
                        String profile_path = movieDetail.getImages().substring(17,endIndex-1);

                        List<Integer> ids = new ArrayList<>();

                        Pattern pattern = Pattern.compile("\"id\":(\\d+)");
                        Matcher matcher = pattern.matcher(movieDetail.getGenres());

                        while (matcher.find()) {
                            ids.add(Integer.parseInt(matcher.group(1)));
                        }

                        String genres = ids.toString();

                        // 추천 영화의 score와 myScore 계산
                        Map<String, Object> score = Map.of(
                                "avgActorSkill", 0.0, "avgDirectorSkill", 0.0, "avgLineSkill", 0.0,
                                "avgMusicSkill", 0.0, "avgSceneSkill", 0.0, "avgStorySkill", 0.0,
                                "totalAverageSkill", 0.0
                        );

                        Map<String, Object> myScore = null;

                        try {
                            // 추천된 영화의 score 가져오기
                            score = reviewService.getAverageSkillsByMovieId(recommendId);

                            // 사용자의 최근 리뷰에서 해당 영화의 myScore 가져오기
                            myScore = reviewService.getLatestReviewSkills(memberId, recommendId);
                        } catch (Exception e) {
                            log.warn("Review data not found for movie ID: {}", recommendId, e);
                            score = Map.of("avgActorSkill", 0.0, "avgDirectorSkill", 0.0, "avgLineSkill", 0.0, "avgMusicSkill", 0.0, "avgSceneSkill", 0.0, "avgStorySkill", 0.0, "totalAverageSkill", 0.0);
                            myScore = null;
                        }

                        return MovieCardDto.builder()
                                .id(movieDetail.getId())
                                .title(movieDetail.getTitle())
                                .overview(movieDetail.getOverview())
                                .poster_path(profile_path)
                                .release_date(movieDetail.getRelease_date())
                                .genre_ids(genres)
                                .score(score)
                                .myScore(myScore)
                                .build();
                    })
                    .filter(Objects::nonNull) // Null 값 제거
                    .collect(Collectors.toList());

            response.put("recommendations", movieCardDtos); // 추천 리스트 저장
            return response;

        } catch (Exception e) {
            log.error("MovieServiceImpl: 추천 리스트를 가져오는 중 오류 발생", e);
            response.put("recommendations", new ArrayList<>());
            return response;
        }
    }



}