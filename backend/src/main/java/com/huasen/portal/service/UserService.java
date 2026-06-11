package com.huasen.portal.service;

import com.huasen.common.entity.User;
import com.huasen.common.exception.BusinessException;
import com.huasen.common.repository.UserRepository;
import com.huasen.common.service.RedisService;
import com.huasen.common.util.AesUtil;
import com.huasen.common.util.JwtUtil;
import com.huasen.common.constant.RedisKeyConstants;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户服务
 * 对应Node.js: user.controller.js中的业务逻辑
 */
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AesUtil aesUtil;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisService redisService;

    public Map<String, Object> login(String accountId, String password) {
        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessException("ERROR", "用户不存在"));

        String decryptedPassword = aesUtil.decrypt(user.getPassword());
        if (!decryptedPassword.equals(password)) {
            throw new BusinessException("ERROR", "账户密码不匹配");
        }

        if (!user.getEnabled()) {
            throw new BusinessException("ERROR", "账号已被冻结");
        }

        String token = jwtUtil.createToken(accountId, user.getCode());

        Map<String, Object> data = new HashMap<>();
        data.put("id", accountId);
        data.put("token", token);
        data.put("name", user.getName());
        data.put("code", user.getCode());
        data.put("headImg", user.getHeadImg());
        data.put("records", user.getRecords());
        data.put("config", user.getConfig());
        return data;
    }

    public void register(String accountId, String password) {
        if (userRepository.existsByAccountId(accountId)) {
            throw new BusinessException("ERROR", "用户已存在");
        }

        String encryptedPassword = aesUtil.encrypt(password);

        User user = new User();
        user.setAccountId(accountId);
        user.setPassword(encryptedPassword);
        // 普通插件用户固定 code=0：仅用于插件登录收藏网链，绝不能拿到后台权限。
        // 注意：JwtAuthFilter 不区分 token 来自 /user/login 还是 /manage/login，
        // 若此处给 code>=2/3，注册用户即可越权调用后台写接口。固定 0 即堵死该路径。
        user.setCode(0);
        user.setEnabled(true);
        user.setTime(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        userRepository.save(user);
    }

    public void backup(String accountId, List<Map<String, Object>> records,
                       Map<String, Object> config, String name, String headImg) {
        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessException("ERROR", "用户不存在"));

        if (records != null) {
            user.setRecords(records);
        }
        if (config != null) {
            user.setConfig(config);
        }
        if (name != null) {
            user.setName(name);
        }
        if (headImg != null) {
            user.setHeadImg(headImg);
        }

        userRepository.save(user);
    }

    public Map<String, Object> consistentFromCloud(String accountId) {
        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessException("ERROR", "用户不存在"));

        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getAccountId());
        data.put("name", user.getName());
        data.put("code", user.getCode());
        data.put("headImg", user.getHeadImg());
        data.put("records", user.getRecords());
        data.put("config", user.getConfig());
        data.put("enabled", user.getEnabled());
        data.put("time", user.getTime());
        return data;
    }

    public void updatePassword(String accountId, String password) {
        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessException("ERROR", "用户不存在"));

        user.setPassword(aesUtil.encrypt(password));
        userRepository.save(user);

        redisService.delete(RedisKeyConstants.POOL_TOKEN + ":" + accountId);
    }

    public User addUser(String accountId, String password, String name, Integer code, Boolean enabled) {
        if (userRepository.existsByAccountId(accountId)) {
            throw new BusinessException("ERROR", "账号已存在");
        }

        User user = new User();
        user.setAccountId(accountId);
        user.setPassword(aesUtil.encrypt(password));
        user.setName(name != null ? name : "花酱");
        // 普通插件用户固定 code=0，忽略传入的 code（见 register 注释的越权说明）。
        user.setCode(0);
        user.setEnabled(enabled != null ? enabled : true);
        user.setTime(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        return userRepository.save(user);
    }

    public Map<String, Object> findByPage(int pageNo, int pageSize, String accountId, String name, Integer code) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (accountId != null && !accountId.isEmpty()) {
                predicates.add(cb.like(root.get("accountId"), "%" + accountId + "%"));
            }
            if (name != null && !name.isEmpty()) {
                predicates.add(cb.like(root.get("name"), "%" + name + "%"));
            }
            if (code != null) {
                predicates.add(cb.equal(root.get("code"), code));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<User> page = userRepository.findAll(spec, PageRequest.of(pageNo - 1, pageSize));

        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getContent());
        result.put("total", page.getTotalElements());
        return result;
    }

    public void removeUser(Long id) {
        userRepository.deleteById(id);
    }

    public void updateUser(Long id, Map<String, Object> params) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ERROR", "用户不存在"));

        if (params.containsKey("name")) {
            user.setName((String) params.get("name"));
        }
        // 不允许通过更新修改普通用户的 code：插件用户恒为 0，防止越权（见 register 注释）。
        if (params.containsKey("enabled")) {
            user.setEnabled((Boolean) params.get("enabled"));
        }
        if (params.containsKey("headImg")) {
            user.setHeadImg((String) params.get("headImg"));
        }
        if (params.containsKey("password")) {
            String newPassword = (String) params.get("password");
            if (!newPassword.equals(user.getPassword())) {
                user.setPassword(aesUtil.encrypt(newPassword));
            }
        }

        userRepository.save(user);
    }

    /**
     * 安全地将参数转换为 Integer。
     * 前端可能传 Number、数字字符串或空字符串，统一处理避免 ClassCastException。
     *
     * @param value        参数原始值
     * @param defaultValue 当值为 null 或空白时返回的默认值
     */
    private Integer toInteger(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(text);
    }
}
