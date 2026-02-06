Feature: Article management
  Authenticated users can manage their published articles

  Background:
    Given a registered user is logged in
    And the author has published an article titled "Test Article"

  Scenario: Retrieve a published article by its slug
    When the author retrieves the article
    Then the article title is "Test Article"

  Scenario: Published articles appear in the global feed
    When a client requests the list of articles
    Then the article list contains at least 1 article

  Scenario: Articles can be filtered by tag
    Given the author has published an article titled "Tagged Article" with tag "kotlin"
    When a client requests articles filtered by tag "kotlin"
    Then the article list contains an article titled "Tagged Article"
    And every article in the list is tagged with "kotlin"
