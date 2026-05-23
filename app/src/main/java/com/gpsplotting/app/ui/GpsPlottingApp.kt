package com.gpsplotting.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gpsplotting.app.ui.screens.HomeDestination
import com.gpsplotting.app.ui.screens.OffsetScreen
import com.gpsplotting.app.ui.screens.RoadBuilderScreen
import com.gpsplotting.app.ui.screens.RoadBuilderV2Screen
import com.gpsplotting.app.ui.screens.SlopeLineScreen
import com.gpsplotting.app.ui.screens.SlopingPlaneScreen

@Composable
fun GpsPlottingApp() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") { HomeScreen(nav) }
        composable("offset") { OffsetScreen(nav) }
        composable("road") { RoadBuilderScreen(nav) }
        composable("road_v2") { RoadBuilderV2Screen(nav) }
        composable("slope_line") { SlopeLineScreen(nav) }
        composable("sloping_plane") { SlopingPlaneScreen(nav) }
    }
}

private val destinations = listOf(
    HomeDestination("offset", "Offsets", "offset 4 corners"),
    HomeDestination("road", "Road Builder V1", "centerline + cross slope %"),
    HomeDestination("road_v2", "Road Builder V2", "V1 + autograde profile"),
    HomeDestination("slope_line", "Slope Calc", "rise/run"),
    HomeDestination("sloping_plane", "Sloping plane", "surface generator"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(nav: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("GPS Plotting") })
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(destinations) { d ->
                Card(
                    onClick = { nav.navigate(d.route) },
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(d.title, style = MaterialTheme.typography.titleMedium)
                        Text(d.subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(
    title: String,
    nav: NavHostController,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = actions,
            )
        },
        content = content,
    )
}
