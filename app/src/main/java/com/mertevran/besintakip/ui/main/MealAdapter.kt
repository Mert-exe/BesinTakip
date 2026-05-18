package com.mertevran.besintakip.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mertevran.besintakip.data.model.Meal
import com.mertevran.besintakip.databinding.ItemMealBinding
import java.text.SimpleDateFormat
import java.util.*

class MealAdapter(
    private var meals: List<Meal>,
    private val onDeleteClick: (Meal) -> Unit = {},
    private val onItemClick: (Meal) -> Unit = {},
    private val onShareClick: (Meal) -> Unit = {}
) : RecyclerView.Adapter<MealAdapter.MealViewHolder>() {

    fun updateMeals(newMeals: List<Meal>) {
        meals = newMeals
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealViewHolder {
        val binding = ItemMealBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MealViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MealViewHolder, position: Int) {
        holder.bind(meals[position], onDeleteClick, onItemClick, onShareClick)
    }

    override fun getItemCount(): Int = meals.size

    class MealViewHolder(private val binding: ItemMealBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(meal: Meal, onDeleteClick: (Meal) -> Unit, onItemClick: (Meal) -> Unit, onShareClick: (Meal) -> Unit) {
            binding.tvMealName.text = meal.name
            binding.tvMealCalories.text = meal.calories.toInt().toString()
            binding.tvMealCategory.text = meal.category
            val sdf = SimpleDateFormat("dd MMMM, HH:mm", Locale("tr"))
            binding.tvMealDate.text = sdf.format(Date(meal.date))
            
            binding.btnDeleteMeal.setOnClickListener {
                onDeleteClick(meal)
            }

            binding.btnShareMeal.setOnClickListener {
                onShareClick(meal)
            }

            binding.root.setOnClickListener {
                onItemClick(meal)
            }
        }
    }
}
