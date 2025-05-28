package com.moveworks;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class UserReportGenerator {
    @JsonIgnoreProperties(ignoreUnknown = true)
    // Simple POJO matching the GoRest /users JSON objects
    public static class User {
        public long id;
        public String email;
        public String status;
    }

    private static final String BASE_URL = "https://gorest.co.in/public/v2/users";
    // Adjust per_page up to the API’s max (100) to reduce number of requests
    private static final int PER_PAGE = 100;

    public static void main(String[] args) throws Exception {
        List<User> allUsers = fetchAllUsers();

        System.out.println("=== Question 1: Active users with .test emails ===");
        generateTestUsersReport(allUsers);

        System.out.println("\n=== Question 2: Email domain suffix counts ===");
        generateDomainCountReport(allUsers);
    }

    /** Fetches all User records by paging through the API until no more results. */
    private static List<User> fetchAllUsers() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        List<User> users = new ArrayList<>();
        int page = 1;

        while (true) {
            String url = String.format("%s?page=%d&per_page=%d", BASE_URL, page, PER_PAGE);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Failed to fetch data: HTTP " + response.statusCode());
            }

            List<User> pageUsers = mapper.readValue(
                    response.body(),
                    new TypeReference<List<User>>() {
                    });

            if (pageUsers.isEmpty()) {
                break; // no more pages
            }

            users.addAll(pageUsers);
            page++;
        }

        return users;
    }

    /** Q1: Prints CSV of active users whose email ends with ".test" */
    private static void generateTestUsersReport(List<User> users) {
        System.out.println("id,email");
        users.stream()
                .filter(u -> "active".equalsIgnoreCase(u.status))
                .filter(u -> u.email.toLowerCase(Locale.ROOT).endsWith(".test"))
                .forEach(u -> System.out.printf("%d,%s%n", u.id, u.email));
    }

    /**
     * Q2: Prints CSV of each email domain suffix and the count of users with that
     * suffix.
     */
    private static void generateDomainCountReport(List<User> users) {
        Map<String, Integer> suffixCounts = new HashMap<>();

        for (User u : users) {
            String email = u.email.toLowerCase(Locale.ROOT);
            int atPos = email.lastIndexOf('@');
            if (atPos < 0 || atPos == email.length() - 1)
                continue; // skip malformed

            String domain = email.substring(atPos + 1); // e.g. "example.test"
            int dotPos = domain.lastIndexOf('.');
            if (dotPos < 0 || dotPos == domain.length() - 1)
                continue;

            String suffix = domain.substring(dotPos + 1); // e.g. "test"
            suffixCounts.merge(suffix, 1, Integer::sum);
        }

        System.out.println("Domain,count");
        suffixCounts.forEach((suffix, count) -> System.out.printf("%s,%d%n", suffix, count));
    }
}
