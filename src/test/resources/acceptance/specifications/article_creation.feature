Feature: Article creation
  Authenticated authors can publish articles with metadata and tags

  Scenario: Create an article with tags
    Given a registered user is logged in
    When the author creates an article with the following details:
      | title       | How to Train a Dragon     |
      | description | An introductory guide     |
      | body        | Dragons require patience. |
    And the article is tagged with:
      | training |
      | dragons  |
      | fantasy  |
    Then the article is published successfully
    And the article details match the provided values
    And the article has the following tags:
      | training |
      | dragons  |
      | fantasy  |