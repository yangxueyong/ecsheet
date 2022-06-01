package com.mars.ecsheet.controller;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mars.ecsheet.entity.WorkBookEntity;
import com.mars.ecsheet.entity.WorkSheetEntity;
import com.mars.ecsheet.repository.WorkBookRepository;
import com.mars.ecsheet.repository.WorkSheetRepository;
import com.mars.ecsheet.utils.SheetUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 * @author Mars
 * @date 2020/10/28
 * @description
 */
@RestController
public class IndexController {

    @Autowired
    private WorkBookRepository workBookRepository;

    @Autowired
    private WorkSheetRepository workSheetRepository;

    @GetMapping("index")
    public ModelAndView index() {
        List<WorkBookEntity> all = workBookRepository.findAll();

        return new ModelAndView("index", "all", all);
    }


    @GetMapping("index/create")
    public void create(HttpServletRequest request, HttpServletResponse response) throws IOException {
        WorkBookEntity wb = new WorkBookEntity();
        wb.setName("default");
        wb.setOption(SheetUtil.getDefautOption());
        WorkBookEntity saveWb = workBookRepository.save(wb);
        //生成sheet数据
        generateSheet(saveWb.getId());
        response.sendRedirect("/index/" + saveWb.getId());
    }


    @GetMapping("/index/{wbId}")
    public ModelAndView index(@PathVariable(value = "wbId") String wbId) {
        Optional<WorkBookEntity> Owb = workBookRepository.findById(wbId);
        WorkBookEntity wb = new WorkBookEntity();
        if (!Owb.isPresent()) {
            wb.setId(wbId);
            wb.setName("default");
            wb.setOption(SheetUtil.getDefautOption());
            WorkBookEntity result = workBookRepository.save(wb);
            generateSheet(wbId);
        } else {
            wb = Owb.get();
        }

        return new ModelAndView("websocket", "wb", wb);
    }

    @PostMapping("/load/{wbId}")
    public String load(@PathVariable(value = "wbId") String wbId) {

        List<WorkSheetEntity> wsList = workSheetRepository.findAllBywbId(wbId);
        List<JSONObject> list = new ArrayList<>();
        wsList.forEach(ws -> {
            JSONObject data = ws.getData();
            if(data!=null){
//                data.remove("hyperlink");
//                data.remove("calcChain");
                data.remove("luckysheet_alternateformat_save");
                data.remove("luckysheet_conditionformat_save");
//                data.remove("config");
                data.remove("images");

                Object configObj = data.get("config");
                if(configObj instanceof String){
                    String config = configObj.toString();
                    data.put("config",JSONUtil.parseObj(config));
                }

                Object calcChainObj = data.get("calcChain");
                if(calcChainObj instanceof String){
                    String calcChain = calcChainObj.toString();
                    data.put("calcChain",JSONUtil.parseArray(calcChain));
                }
//                calcData(data);
            }
            list.add(data);
        });


        return JSONUtil.toJsonStr(list);
    }

    private void calcData(JSONObject data) {
        JSONArray celldata = data.getJSONArray("celldata");
        if(celldata == null || celldata.size() == 0){
            return;
        }
        Map<String,JSONObject> objMap = new HashMap<>();
        List<JSONObject> removes = new ArrayList<>();
        for (int i = 0; i < celldata.size(); i++) {
            JSONObject dataObj = celldata.getJSONObject(i);
            Integer r = dataObj.getInt("r");
            Integer c = dataObj.getInt("c");
            JSONObject v = dataObj.getJSONObject("v");
            if(v == null || "".equals(v.toString().trim()) || "{}".equals(v.toString().trim()) || "[]".equals(v.toString().trim())){
                removes.add(dataObj);
                continue;
            }
            String key = r + ":" + c;
            if(!objMap.containsKey(key)){
                objMap.put(key,dataObj);
            }else{
                JSONObject mapObj = objMap.get(key);
                String f = mapObj.getStr("f");
                Set<String> keys = dataObj.keySet();
                if(keys == null || keys.size() == 0) {
                    continue;
                }
                for (String dataKey : keys) {
                    if("f".equals(dataKey) && !"".equals(f)){
                        continue;
                    }
                    mapObj.put(dataKey,dataObj.get(dataKey));
                }
            }
        }

        JSONArray newcelldata = new JSONArray();
        if(objMap != null && objMap.size() > 0){
            for (JSONObject value : objMap.values()) {
                newcelldata.add(value);
            }
        }
        data.put("celldata",newcelldata);
    }


    @PostMapping("/loadSheet/{wbId}")
    public String loadSheet(@PathVariable(value = "wbId") String wbId) {
        List<WorkSheetEntity> wsList = workSheetRepository.findAllBywbId(wbId);
        List<JSONObject> list = new ArrayList<>();
        wsList.forEach(ws -> {
            list.add(ws.getData());
        });
        if (!list.isEmpty()) {
            return SheetUtil.buildSheetData(list).toString();
        }
        return SheetUtil.getDefaultAllSheetData().toString();
    }


    private void generateSheet(String wbId) {
        SheetUtil.getDefaultSheetData().forEach(jsonObject -> {
            WorkSheetEntity ws = new WorkSheetEntity();
            ws.setWbId(wbId);
            ws.setData(jsonObject);
            ws.setDeleteStatus(0);
            workSheetRepository.save(ws);
        });
    }

}
