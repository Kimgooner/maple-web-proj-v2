package org.whitedoggy.mapleweb2.domain.item.data;

import lombok.Getter;
import lombok.Setter;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

import java.util.List;

@Getter
@Setter
public class ItemSheet {
    String itemName;
    String itemIcon;
    String itemDescription;
    String expired;

    Integer starForce;

    List<String> itemTotalOption;
    List<String> itemExceptionalOption;

    String PotentialGrade;
    List<String> itemPotentialOption;

    String AdditionalPotentialGrade;
    List<String> itemAdditionalPotentialOption;

    String itemSoulName;
    String itemSoulOption;

    public ItemSheet(String itemName){
        this.itemName = itemName;
    }

    public void addTotalOption(String option){
        this.itemTotalOption.add(option);
    }

    public void addExceptionalOption(String option){
        this.itemExceptionalOption.add(option);
    }

    public void addPotentialOption(String option){
        this.itemPotentialOption.add(option);
    }

    public void addAdditionalPotentialOption(String option){
        this.itemAdditionalPotentialOption.add(option);
    }
}
