Feature: Tags
  The API exposes available tags

  Scenario: Retrieve tags from a fresh system
    When a client requests the list of tags
    Then the response is a list of tags