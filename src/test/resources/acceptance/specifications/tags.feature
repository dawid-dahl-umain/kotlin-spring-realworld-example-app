Feature: Tags
  The API exposes available tags

  Scenario: Published tags appear in the tag list
    Given a registered user is logged in
    And the author has published an article titled "Tag Test" with tag "cucumber"
    When a client requests the list of tags
    Then the tag list includes "cucumber"
