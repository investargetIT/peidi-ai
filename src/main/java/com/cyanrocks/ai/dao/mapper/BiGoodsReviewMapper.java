package com.cyanrocks.ai.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyanrocks.ai.dao.entity.BiGoodsReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BiGoodsReviewMapper extends BaseMapper<BiGoodsReview> {
    @Select("select t1.* from goods_review t1 left join bi_goods_review t2 on t2.customer_name = t1.customer_name " +
            " AND t2.goods_review = t1.goods_review " +
            " and t2.review_date = t1.review_date" +
            " and t2.goods_id =t1.goods_id where t2.id is null")
    List<BiGoodsReview> selectAll();

    @Select("select * from bi_goods_review")
    List<BiGoodsReview> selectAl1l();
}
