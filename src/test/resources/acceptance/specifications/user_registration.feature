Feature: User registration
  New users can create accounts with valid credentials

  Scenario: Register a new user successfully
    When a new user registers with username "testuser", email "test@example.com", and password "password123"
    Then the user is successfully registered

  Scenario Outline: Reject duplicate registration
    Given a user already exists with username "existing", email "existing@example.com", and password "password123"
    When a new user registers with username "<username>", email "<email>", and password "<password>"
    Then registration is rejected with an error on the "<field>" field

    Examples: Duplicate fields
      | username | email                | password    | field    |
      | existing | unique@example.com   | password123 | username |
      | unique   | existing@example.com | password123 | email    |
