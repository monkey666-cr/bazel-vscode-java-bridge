package com.example.api;

import java.util.List;
import java.util.Optional;

public interface UserService {
    Optional<DataModel.User> findById(String id);
    List<DataModel.User> findAll();
    DataModel.User save(DataModel.User user);
}
