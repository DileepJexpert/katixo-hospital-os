import 'package:flutter/material.dart';
import '../../core/widgets/security_button.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_state.dart';
import '../../core/theme/design_tokens.dart';
import '../../core/widgets/app_shell.dart';
import '../clinical/emr_chart_screen.dart';
import '../certificate/certificate_screen.dart';
import '../consent/consent_screen.dart';
import '../discharge/discharge_summary_screen.dart';
import '../lab/lab_screen.dart';
import '../nursing/nursing_screen.dart';
import '../nursing/vitals_screen.dart';
import '../ot/ot_screen.dart';
import '../prescription/prescriptions_screen.dart';
import '../radiology/radiology_screen.dart';
import 'doctor_leave_screen.dart';
import 'doctor_worklist_screen.dart';

/// Doctor role home: consult worklist, lab and ward indents.
class DoctorHome extends StatefulWidget {
  const DoctorHome({super.key});

  @override
  State<DoctorHome> createState() => _DoctorHomeState();
}

class _DoctorHomeState extends State<DoctorHome> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final authState = context.watch<AuthState>();
    final theme = Theme.of(context);

    return AppShell(
      title: 'Doctor',
      destinations: const [
        ShellDestination(
          label: 'Worklist',
          icon: Icons.list_alt_outlined,
          selectedIcon: Icons.list_alt,
        ),
        ShellDestination(
          label: 'Lab',
          icon: Icons.science_outlined,
          selectedIcon: Icons.science,
        ),
        ShellDestination(
          label: 'Ward Indents',
          icon: Icons.assignment_outlined,
          selectedIcon: Icons.assignment,
        ),
        ShellDestination(
          label: 'Vitals',
          icon: Icons.monitor_heart_outlined,
          selectedIcon: Icons.monitor_heart,
        ),
        ShellDestination(
          label: 'Prescriptions',
          icon: Icons.medical_information_outlined,
          selectedIcon: Icons.medical_information,
        ),
        ShellDestination(
          label: 'My Leave',
          icon: Icons.event_busy_outlined,
          selectedIcon: Icons.event_busy,
        ),
        ShellDestination(
          label: 'OT',
          icon: Icons.medical_services_outlined,
          selectedIcon: Icons.medical_services,
        ),
        ShellDestination(
          label: 'Radiology',
          icon: Icons.scanner_outlined,
          selectedIcon: Icons.scanner,
        ),
        ShellDestination(
          label: 'Consent',
          icon: Icons.assignment_turned_in_outlined,
          selectedIcon: Icons.assignment_turned_in,
        ),
        ShellDestination(
          label: 'Certificates',
          icon: Icons.workspace_premium_outlined,
          selectedIcon: Icons.workspace_premium,
        ),
        ShellDestination(
          label: 'Discharge Summaries',
          icon: Icons.summarize_outlined,
          selectedIcon: Icons.summarize,
        ),
        ShellDestination(
          label: 'EMR Chart',
          icon: Icons.assignment_ind_outlined,
          selectedIcon: Icons.assignment_ind,
        ),
      ],
      selectedIndex: _index,
      onDestinationSelected: (i) => setState(() => _index = i),
      actions: [
        if (authState.currentUser != null)
          Center(
            child: Padding(
              padding: const EdgeInsets.only(right: Space.sm),
              child: Text(authState.currentUser!.name,
                  style: theme.textTheme.labelLarge),
            ),
          ),
        const SecurityButton(),
        IconButton(
          tooltip: 'Sign out',
          icon: const Icon(Icons.logout_outlined),
          onPressed: () => authState.logout(),
        ),
      ],
      body: switch (_index) {
        2 => const NursingScreen(),
        1 => const LabScreen(),
        3 => const VitalsScreen(),
        4 => const PrescriptionsScreen(),
        5 => const DoctorLeaveScreen(),
        6 => const OtScreen(),
        7 => const RadiologyScreen(),
        8 => const ConsentScreen(),
        9 => const CertificateScreen(),
        10 => const DischargeSummaryScreen(),
        11 => const EmrChartScreen(),
        _ => const DoctorWorklistScreen(),
      },
    );
  }
}
